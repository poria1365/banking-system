package com.banking.application.saga;

import com.banking.domain.event.*;
import com.banking.domain.model.Account;
import com.banking.domain.model.Money;
import com.banking.domain.model.Transaction;
import com.banking.domain.port.out.AccountRepository;
import com.banking.domain.port.out.EventPublisher;
import com.banking.domain.port.out.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Orchestrator-based Saga for money transfers.
 *
 * Steps:
 *   1. Debit source account
 *   2. Credit target account
 *   3. Mark transaction COMPLETED
 *
 * Failure branches (checked explicitly with both flags):
 *   !debit && !credit  → Scenario A: nothing persisted; mark FAILED, no rollback
 *    debit && !credit  → Scenario B: debit persisted, credit failed; reverse debit
 *                        B1. Reversal also fails → NEEDS_MANUAL_REVIEW
 *    debit &&  credit  → Scenario C: accounts correct, status save failed;
 *                        do NOT touch accounts — NEEDS_MANUAL_REVIEW
 *   !debit &&  credit  → Impossible; guarded with NEEDS_MANUAL_REVIEW + ERROR log
 *
 * The caller must hold distributed locks on both accounts before invoking execute().
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransferSagaOrchestrator {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final EventPublisher eventPublisher;

    public void execute(Transaction transaction, Account source, Account target, Money amount) {
        log.info("Saga started: transactionId={} from={} to={} amount={}",
            transaction.getId(), source.getId(), target.getId(), amount);

        transaction.markProcessing();
        transactionRepository.save(transaction);
        eventPublisher.publish(new TransferInitiatedEvent(
            transaction.getId(), source.getId(), target.getId(), amount));

        boolean debitSucceeded  = false;
        boolean creditSucceeded = false;

        try {
            // Step 1 — Debit source
            source.debit(amount);
            accountRepository.save(source);
            debitSucceeded = true;
            eventPublisher.publish(new AccountDebitedEvent(transaction.getId(), source.getId(), amount));
            log.debug("Saga step 1 complete: source account debited");

            // Step 2 — Credit target
            target.credit(amount);
            accountRepository.save(target);
            creditSucceeded = true;
            eventPublisher.publish(new AccountCreditedEvent(transaction.getId(), target.getId(), amount));
            log.debug("Saga step 2 complete: target account credited");

            // Step 3 — Finalize
            transaction.markCompleted();
            transactionRepository.save(transaction);
            eventPublisher.publish(new TransferCompletedEvent(transaction.getId()));
            log.info("Saga completed successfully: transactionId={}", transaction.getId());

        } catch (Exception primaryFailure) {
            log.warn("Saga failed: transactionId={} debitSucceeded={} creditSucceeded={} reason={}",
                transaction.getId(), debitSucceeded, creditSucceeded, primaryFailure.getMessage());

            if (!debitSucceeded && !creditSucceeded) {
                // Scenario A: nothing was persisted — safe to mark failed, no account rollback needed
                transaction.markFailed(primaryFailure.getMessage());
                transactionRepository.save(transaction);
                eventPublisher.publish(new TransferFailedEvent(transaction.getId(), primaryFailure.getMessage()));

            } else if (debitSucceeded && !creditSucceeded) {
                // Scenario B: debit persisted but credit failed — reverse the debit
                compensateDebit(transaction, source, amount, primaryFailure);

            } else if (debitSucceeded && creditSucceeded) {
                // Scenario C: both accounts updated successfully; only the status save failed.
                // Compensating would reverse a transfer that already happened correctly — do NOT touch accounts.
                String reason = "Transfer succeeded but status record failed: " + primaryFailure.getMessage();
                log.error("CRITICAL: accounts are consistent but transaction record could not be finalised. " +
                          "transactionId={} reason={}", transaction.getId(), reason, primaryFailure);
                transaction.markNeedsManualReview(reason);
                transactionRepository.save(transaction);
                eventPublisher.publish(new TransferFailedEvent(transaction.getId(), reason));

            } else {
                // !debitSucceeded && creditSucceeded — logically impossible:
                // credit runs after debit in the try block, so credit cannot succeed if debit did not.
                log.error("CRITICAL: impossible saga state — creditSucceeded=true but debitSucceeded=false. " +
                          "transactionId={}", transaction.getId(), primaryFailure);
                transaction.markNeedsManualReview("Impossible saga state: " + primaryFailure.getMessage());
                transactionRepository.save(transaction);
                eventPublisher.publish(new TransferFailedEvent(transaction.getId(), primaryFailure.getMessage()));
            }
        }
    }

    private void compensateDebit(Transaction transaction, Account source, Money amount, Exception cause) {
        transaction.markCompensating();
        transactionRepository.save(transaction);
        log.warn("Compensating: reversing debit on account={} amount={}", source.getId(), amount);

        try {
            source.credit(amount);
            accountRepository.save(source);

            transaction.markCompensated(cause.getMessage());
            transactionRepository.save(transaction);
            eventPublisher.publish(new DebitReversedEvent(transaction.getId(), source.getId(), amount));
            eventPublisher.publish(new TransferFailedEvent(transaction.getId(), cause.getMessage()));
            log.info("Compensation successful: transactionId={}", transaction.getId());

        } catch (Exception compensationFailure) {
            // Compensation itself failed — requires human intervention
            String combinedReason = "Primary: " + cause.getMessage()
                + " | Compensation: " + compensationFailure.getMessage();
            transaction.markNeedsManualReview(combinedReason);
            transactionRepository.save(transaction);
            eventPublisher.publish(new TransferFailedEvent(transaction.getId(), combinedReason));

            log.error("CRITICAL: Saga compensation failed for transactionId={}. Manual review required. Reason: {}",
                transaction.getId(), combinedReason, compensationFailure);
        }
    }
}
