package com.banking.application.saga;

import com.banking.domain.event.*;
import com.banking.domain.exception.DomainException;
import com.banking.domain.model.Account;
import com.banking.domain.model.Money;
import com.banking.domain.model.Transaction;
import com.banking.domain.port.out.AccountRepository;
import com.banking.domain.port.out.EventPublisher;
import com.banking.domain.port.out.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// Orchestrator-based Saga for money transfers.
//
// Three phases:
//   1. Mark PROCESSING (own DB transaction) — crash-recovery signal; a recovery job
//      can detect rows stuck in PROCESSING and alert ops.
//   2. Atomic debit+credit inside TransactionTemplate — if anything throws, the DB
//      rolls back both saves automatically. No application-level compensation needed.
//   3. Mark COMPLETED — if only this save fails (Scenario C), balances are already
//      correct; mark NEEDS_MANUAL_REVIEW rather than compensating.
//
// DomainException out of Phase 2  → definitive business failure, mark FAILED, stop.
// Any other exception out of Phase 2 → propagates up so @Retry re-attempts the saga.
@Component
@RequiredArgsConstructor
@Slf4j
public class TransferSagaOrchestrator {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final EventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public void execute(Transaction transaction, Account source, Account target, Money amount) {
        log.info("Saga started: transactionId={} from={} to={} amount={}",
            transaction.getId(), source.getId(), target.getId(), amount);

        // Phase 1 — mark intent before touching any balance; short-lived own transaction
        transaction.markProcessing();
        transactionRepository.save(transaction);
        eventPublisher.publish(new TransferInitiatedEvent(
            transaction.getId(), source.getId(), target.getId(), amount));

        // Phase 2 — debit and credit in a single DB transaction; either both commit or neither does
        try {
            transactionTemplate.execute(status -> {
                source.debit(amount);
                accountRepository.save(source);
                target.credit(amount);
                accountRepository.save(target);
                return null;
            });
        } catch (DomainException businessFailure) {
            // Insufficient funds, frozen account, etc. DB rolled back — no money moved.
            // Definitive failure; don't re-throw (retrying won't help).
            log.warn("Saga failed (business rule): transactionId={} reason={}",
                transaction.getId(), businessFailure.getMessage());
            transaction.markFailed(businessFailure.getMessage());
            transactionRepository.save(transaction);
            eventPublisher.publish(new TransferFailedEvent(transaction.getId(), businessFailure.getMessage()));
            return;
        }
        // Transient failures (DB timeouts, network) propagate uncaught — @Retry on
        // TransferMoneyService picks them up and re-enters from the top.

        // Publish AFTER the atomic commit, not inside the template — avoids spurious
        // events if the template had to roll back.
        eventPublisher.publish(new AccountDebitedEvent(transaction.getId(), source.getId(), amount));
        eventPublisher.publish(new AccountCreditedEvent(transaction.getId(), target.getId(), amount));

        // Phase 3 — record final status; if this save fails the balances are already correct
        try {
            transaction.markCompleted();
            transactionRepository.save(transaction);
            eventPublisher.publish(new TransferCompletedEvent(transaction.getId()));
            log.info("Saga completed: transactionId={}", transaction.getId());
        } catch (Exception statusFailure) {
            // Scenario C: money is where it should be; only the status row failed.
            // Compensating here would incorrectly reverse a correct transfer.
            String reason = "Balances transferred but status record failed: " + statusFailure.getMessage();
            log.error("CRITICAL: transactionId={} — {}", transaction.getId(), reason, statusFailure);
            transaction.markNeedsManualReview(reason);
            transactionRepository.save(transaction);
            eventPublisher.publish(new TransferFailedEvent(transaction.getId(), reason));
        }
    }
}
