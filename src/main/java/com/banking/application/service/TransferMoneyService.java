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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Orchestrates the transfer flow: idempotency check → accounts loaded → intent saved →
// locks acquired → saga executed. Order matters — see inline comments.
@UseCase
@RequiredArgsConstructor
@Slf4j
public class TransferMoneyService implements TransferMoneyUseCase {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final DistributedLockPort distributedLock;
    private final TransferSagaOrchestrator sagaOrchestrator;

    @Override
    public Transaction transfer(TransferMoneyCommand command) {
        // Early exit if this key was already processed — idempotent replay
        Optional<Transaction> existing = transactionRepository
            .findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            log.info("Idempotent replay for key={}, returning existing transaction={}",
                command.idempotencyKey(), existing.get().getId());
            return existing.get();
        }

        Account source = accountRepository.findById(command.fromAccountId())
            .orElseThrow(() -> new AccountNotFoundException(command.fromAccountId()));
        Account target = accountRepository.findById(command.toAccountId())
            .orElseThrow(() -> new AccountNotFoundException(command.toAccountId()));

        // Save PENDING before acquiring locks so intent is recorded even if the process crashes.
        // The DB UNIQUE constraint on idempotency_key also catches concurrent duplicates here.
        Transaction transaction = Transaction.createTransfer(
            command.idempotencyKey(),
            command.fromAccountId(),
            command.toAccountId(),
            command.amount()
        );
        transactionRepository.save(transaction);

        // Sort IDs to guarantee consistent lock-acquisition order across all JVM instances,
        // preventing deadlock when two transfers involve the same pair of accounts.
        List<UUID> lockOrder = List.of(command.fromAccountId(), command.toAccountId())
            .stream()
            .sorted()
            .toList();

        distributedLock.executeWithLocks(lockOrder, () -> {
            // Re-read inside the lock — a concurrent duplicate may have already completed
            // the saga between our idempotency check above and this point.
            Transaction current = transactionRepository
                .findById(transaction.getId())
                .orElseThrow();
            if (current.isTerminal()) {
                log.info("Transaction {} already terminal ({}), skipping saga",
                    current.getId(), current.getStatus());
                return;
            }
            sagaOrchestrator.execute(current, source, target, command.amount());
        });

        // Read final state after the lock is released
        return transactionRepository.findById(transaction.getId()).orElseThrow();
    }
}
