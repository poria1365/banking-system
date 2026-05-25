package com.banking.adapter.out.persistence.entity;

import com.banking.domain.model.AccountStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the accounts table.
 *
 * @Version enables optimistic locking at the DB level:
 * Hibernate appends "WHERE id = ? AND version = ?" on every UPDATE.
 * If another transaction committed first, Hibernate throws OptimisticLockException
 * which propagates as a 409-conflict or triggers a retry in the caller.
 *
 * This is the second line of defence against double-spend.
 * The first is the distributed Redis lock in RedissonDistributedLock.
 */
@Entity
@Table(
    name = "accounts",
    indexes = {
        @Index(name = "idx_accounts_owner_id", columnList = "owner_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class AccountEntity {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Optimistic locking version — managed exclusively by JPA/Hibernate.
     * Never set this manually; Hibernate increments it on every successful UPDATE.
     */
    @Version
    @Column(nullable = false)
    private Long version;
}
