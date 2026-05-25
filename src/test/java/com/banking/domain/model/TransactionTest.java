package com.banking.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class TransactionTest {

    private UUID fromAccountId;
    private UUID toAccountId;
    private Money amount;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        fromAccountId = UUID.randomUUID();
        toAccountId = UUID.randomUUID();
        amount = Money.of("250.00", "USD");
        transaction = Transaction.createTransfer("idem-key-001", fromAccountId, toAccountId, amount);
    }

    // --- Creation ---

    @Test
    void createTransfer_setsCorrectInitialState() {
        assertThat(transaction.getId()).isNotNull();
        assertThat(transaction.getIdempotencyKey()).isEqualTo("idem-key-001");
        assertThat(transaction.getFromAccountId()).isEqualTo(fromAccountId);
        assertThat(transaction.getToAccountId()).isEqualTo(toAccountId);
        assertThat(transaction.getAmount()).isEqualTo(amount);
        assertThat(transaction.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(transaction.getFailureReason()).isNull();
        assertThat(transaction.getCreatedAt()).isNotNull();
        assertThat(transaction.getCompletedAt()).isNull();
    }

    // --- Lifecycle transitions ---

    @Test
    void markProcessing_transitionsToProcessing() {
        transaction.markProcessing();

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PROCESSING);
    }

    @Test
    void markCompleted_transitionsToCompleted_andSetsCompletedAt() {
        transaction.markProcessing();
        transaction.markCompleted();

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transaction.getCompletedAt()).isNotNull();
        assertThat(transaction.getFailureReason()).isNull();
    }

    @Test
    void markFailed_transitionsToFailed_andSetsReason() {
        transaction.markFailed("Insufficient funds");

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(transaction.getFailureReason()).isEqualTo("Insufficient funds");
    }

    @Test
    void markCompensating_transitionsToCompensating() {
        transaction.markProcessing();
        transaction.markCompensating();

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPENSATING);
    }

    @Test
    void markCompensated_transitionsToCompensated_andPreservesReason() {
        transaction.markProcessing();
        transaction.markCompensating();
        transaction.markCompensated("credit failed: target account frozen");

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPENSATED);
        assertThat(transaction.getFailureReason()).isEqualTo("credit failed: target account frozen");
    }

    @Test
    void markNeedsManualReview_transitionsToNeedsManualReview() {
        transaction.markProcessing();
        transaction.markCompensating();
        transaction.markNeedsManualReview("Primary: credit failed | Compensation: DB error");

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.NEEDS_MANUAL_REVIEW);
        assertThat(transaction.getFailureReason()).contains("Primary:").contains("Compensation:");
    }

    @Test
    void updatedAt_changesOnEveryTransition() throws InterruptedException {
        var t0 = transaction.getUpdatedAt();
        Thread.sleep(5);
        transaction.markProcessing();
        var t1 = transaction.getUpdatedAt();
        Thread.sleep(5);
        transaction.markCompleted();
        var t2 = transaction.getUpdatedAt();

        assertThat(t1).isAfter(t0);
        assertThat(t2).isAfter(t1);
    }

    // --- isTerminal ---

    @ParameterizedTest
    @EnumSource(value = TransactionStatus.class,
        names = {"COMPLETED", "FAILED", "COMPENSATED", "NEEDS_MANUAL_REVIEW"})
    void isTerminal_returnsTrue_forTerminalStatuses(TransactionStatus status) {
        Transaction t = buildTransactionWithStatus(status);
        assertThat(t.isTerminal()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = TransactionStatus.class,
        names = {"PENDING", "PROCESSING", "COMPENSATING"})
    void isTerminal_returnsFalse_forNonTerminalStatuses(TransactionStatus status) {
        Transaction t = buildTransactionWithStatus(status);
        assertThat(t.isTerminal()).isFalse();
    }

    private Transaction buildTransactionWithStatus(TransactionStatus status) {
        Transaction t = Transaction.createTransfer("key", UUID.randomUUID(), UUID.randomUUID(),
            Money.of("10.00", "USD"));
        switch (status) {
            case PROCESSING -> t.markProcessing();
            case COMPLETED -> { t.markProcessing(); t.markCompleted(); }
            case FAILED -> t.markFailed("reason");
            case COMPENSATING -> { t.markProcessing(); t.markCompensating(); }
            case COMPENSATED -> { t.markProcessing(); t.markCompensating(); t.markCompensated("reason"); }
            case NEEDS_MANUAL_REVIEW -> { t.markProcessing(); t.markCompensating(); t.markNeedsManualReview("reason"); }
            default -> { /* PENDING — no-op */ }
        }
        return t;
    }
}
