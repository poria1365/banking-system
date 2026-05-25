package com.banking.adapter.in.web.dto;

import com.banking.domain.model.Transaction;
import com.banking.domain.model.TransactionStatus;
import com.banking.domain.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Read model for the API — flat representation of a Transaction domain object.
// completedAt is null until the saga reaches a terminal state.
public record TransactionResponse(
    UUID id,
    String idempotencyKey,
    UUID fromAccountId,
    UUID toAccountId,
    BigDecimal amount,
    String currency,
    TransactionType type,
    TransactionStatus status,
    String failureReason,
    Instant createdAt,
    Instant completedAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
            transaction.getId(),
            transaction.getIdempotencyKey(),
            transaction.getFromAccountId(),
            transaction.getToAccountId(),
            transaction.getAmount().getAmount(),
            transaction.getAmount().getCurrency(),
            transaction.getType(),
            transaction.getStatus(),
            transaction.getFailureReason(),
            transaction.getCreatedAt(),
            transaction.getCompletedAt()
        );
    }
}
