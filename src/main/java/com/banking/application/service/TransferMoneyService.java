package com.banking.application.service;

import com.banking.application.saga.TransferSagaOrchestrator;
import com.banking.domain.exception.AccountNotFoundException;
import com.banking.domain.model.Account;
import com.banking.domain.model.Transaction;
import com.banking.infrastructure.annotation.UseCase;
import com.banking.domain.port.in.TransferMoneyUseCase;
import com.banking.domain.port.in.command.TransferMoneyCommand;
import com.banking.domain.port.out.AccountRepository;
import com.banking.domain.port.out.DistributedLockPort;
import com.banking.domain.port.out.TransactionRepository;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Orchestrates the transfer flow: idempotency check → validate accounts → save intent →
// acquire locks → re-read fresh accounts → run saga.
//
// Key design decisions:
//   - Idempotency check returns early only for TERMINAL transactions. PENDING/PROCESSING means
//     a prior attempt is incomplete — we continue it rather than returning stale state.
//   - Accounts are loaded INSIDE the lock, not before. Loading before the lock risks using
//     a stale balance if another transfer committed between our read and our lock acquisition.
//   - @Retry retries the whole method on transient failures (optimistic lock collision).
//     Because non-terminal transactions fall through the idempotency check, the retry actually
//     re-runs the saga rather than returning the incomplete transaction immediately.
@UseCase
@RequiredArgsConstructor
@Slf4j
public class TransferMoneyService implements TransferMoneyUseCase {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final DistributedLockPort distributedLock;
    private final TransferSagaOrchestrator sagaOrchestrator;

    @Override
    @Retry(name = "transferService")
    public Transaction transfer(TransferMoneyCommand command) {
        // Return early only if the transaction has already reached a terminal state.
        // Non-terminal (PENDING, PROCESSING) means a prior attempt is in-flight or was interrupted —
        // fall through and try to complete it.
        Optional<Transaction> existing = transactionRepository
            .findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent() && existing.get().isTerminal()) {
            log.info("Idempotent replay for key={} status={}",
                command.idempotencyKey(), existing.get().getStatus());
            return existing.get();
        }

        // Fail fast before touching locks: verify both accounts exist.
        // Uses existsById (no full load) — the actual Account objects are loaded inside the lock.
        if (!accountRepository.existsById(command.fromAccountId())) {
            throw new AccountNotFoundException(command.fromAccountId());
        }
        if (!accountRepository.existsById(command.toAccountId())) {
            throw new AccountNotFoundException(command.toAccountId());
        }

        // On a first attempt, create and persist PENDING to record intent before acquiring locks.
        // On a retry, the PENDING/PROCESSING transaction already exists — reuse it.
        // The DB UNIQUE constraint on idempotency_key is the last-resort guard for concurrent
        // requests that both pass the idempotency check before either saves.
        final Transaction transaction = existing
            .orElseGet(() -> transactionRepository.save(
                Transaction.createTransfer(
                    command.idempotencyKey(),
                    command.fromAccountId(),
                    command.toAccountId(),
                    command.amount()
                )
            ));

        // Sort IDs so every instance acquires locks in the same order — prevents deadlock
        // when two concurrent transfers share a common account.
        List<UUID> lockOrder = List.of(command.fromAccountId(), command.toAccountId())
            .stream().sorted().toList();

        distributedLock.executeWithLocks(lockOrder, () -> {
            // Re-check status inside the lock: a concurrent duplicate may have completed
            // the saga between the idempotency check above and this point.
            Transaction current = transactionRepository
                .findById(transaction.getId()).orElseThrow();
            if (current.isTerminal()) {
                log.info("Transaction {} already terminal ({}), skipping saga",
                    current.getId(), current.getStatus());
                return;
            }

            // Load accounts NOW, inside the lock — guaranteed fresh data.
            // Any other transfer that held these locks has already committed, so the balances
            // we read here are exactly what the DB has at this moment.
            Account source = accountRepository.findById(command.fromAccountId())
                .orElseThrow(() -> new AccountNotFoundException(command.fromAccountId()));
            Account target = accountRepository.findById(command.toAccountId())
                .orElseThrow(() -> new AccountNotFoundException(command.toAccountId()));

            sagaOrchestrator.execute(current, source, target, command.amount());
        });

        return transactionRepository.findById(transaction.getId()).orElseThrow();
    }
}
