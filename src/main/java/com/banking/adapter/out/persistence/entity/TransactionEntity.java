package com.banking.adapter.out.persistence.entity;

import com.banking.domain.model.TransactionStatus;
import com.banking.domain.model.TransactionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the transactions table.
 *
 * idempotencyKey has a UNIQUE constraint at the DB level — this is the
 * last-resort guard against duplicate inserts that slip past the
 * application-level idempotency check (e.g., two concurrent requests
 * with the same key that both pass the check before either saves).
 *
 * Indexes on fromAccountId and toAccountId support efficient account
 * history queries without full-table scans.
 */
@Entity
@Table(
    name = "transactions",
    indexes = {
        @Index(name = "idx_txn_from_account_id", columnList = "from_account_id"),
        @Index(name = "idx_txn_to_account_id",   columnList = "to_account_id"),
        @Index(name = "idx_txn_created_at",       columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class TransactionEntity {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64, updatable = false)
    private String idempotencyKey;

    @Column(name = "from_account_id", nullable = false, updatable = false)
    private UUID fromAccountId;

    @Column(name = "to_account_id", nullable = false, updatable = false)
    private UUID toAccountId;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionStatus status;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
