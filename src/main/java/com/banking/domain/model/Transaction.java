package com.banking.domain.model;

import java.time.Instant;
import java.util.UUID;

// Tracks the full lifecycle of a transfer from PENDING through to a terminal state.
// The status transitions are intentionally one-way — you can't un-complete a transaction.
public class Transaction {

    private final UUID id;
    private final String idempotencyKey;
    private final UUID fromAccountId;
    private final UUID toAccountId;
    private final Money amount;
    private final TransactionType type;
    private TransactionStatus status;
    private String failureReason;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    private Transaction(UUID id, String idempotencyKey, UUID fromAccountId, UUID toAccountId,
                        Money amount, TransactionType type, TransactionStatus status,
                        String failureReason, Instant createdAt, Instant updatedAt,
                        Instant completedAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.type = type;
        this.status = status;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.completedAt = completedAt;
    }

    // New transfer — always starts as PENDING so intent is recorded before any money moves
    public static Transaction createTransfer(String idempotencyKey, UUID fromAccountId,
                                              UUID toAccountId, Money amount) {
        Instant now = Instant.now();
        return new Transaction(
            UUID.randomUUID(),
            idempotencyKey,
            fromAccountId,
            toAccountId,
            amount,
            TransactionType.TRANSFER,
            TransactionStatus.PENDING,
            null,
            now,
            now,
            null
        );
    }

    // Rebuild from DB row — all fields provided explicitly
    public static Transaction reconstitute(UUID id, String idempotencyKey, UUID fromAccountId,
                                            UUID toAccountId, Money amount, TransactionType type,
                                            TransactionStatus status, String failureReason,
                                            Instant createdAt, Instant updatedAt,
                                            Instant completedAt) {
        return new Transaction(id, idempotencyKey, fromAccountId, toAccountId, amount, type,
            status, failureReason, createdAt, updatedAt, completedAt);
    }

    public void markProcessing() {
        this.status = TransactionStatus.PROCESSING;
        touch();
    }

    public void markCompleted() {
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = Instant.now();
        touch();
    }

    // Scenario A: debit never happened — nothing to reverse
    public void markFailed(String reason) {
        this.status = TransactionStatus.FAILED;
        this.failureReason = reason;
        touch();
    }

    // Scenario B step 1: debit happened, credit failed — kicking off compensation
    public void markCompensating() {
        this.status = TransactionStatus.COMPENSATING;
        touch();
    }

    // Scenario B step 2: debit successfully reversed
    public void markCompensated(String reason) {
        this.status = TransactionStatus.COMPENSATED;
        this.failureReason = reason;
        touch();
    }

    // Scenario B1 or C: something went wrong that needs a human to look at
    public void markNeedsManualReview(String reason) {
        this.status = TransactionStatus.NEEDS_MANUAL_REVIEW;
        this.failureReason = reason;
        touch();
    }

    // Terminal = no further automatic action will be taken by the system
    public boolean isTerminal() {
        return status == TransactionStatus.COMPLETED
            || status == TransactionStatus.FAILED
            || status == TransactionStatus.COMPENSATED
            || status == TransactionStatus.NEEDS_MANUAL_REVIEW;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getFromAccountId() { return fromAccountId; }
    public UUID getToAccountId() { return toAccountId; }
    public Money getAmount() { return amount; }
    public TransactionType getType() { return type; }
    public TransactionStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
