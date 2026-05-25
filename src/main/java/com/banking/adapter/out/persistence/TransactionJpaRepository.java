package com.banking.adapter.out.persistence;

import com.banking.adapter.out.persistence.entity.TransactionEntity;
import com.banking.domain.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Spring Data JPA repository for the transactions table.
// findByIdempotencyKey is the application-level duplicate check.
// The DB UNIQUE constraint on idempotency_key is the safety net for concurrent races.
public interface TransactionJpaRepository extends JpaRepository<TransactionEntity, UUID> {

    Optional<TransactionEntity> findByIdempotencyKey(String idempotencyKey);

    // Both directions ordered newest-first for the account history view
    List<TransactionEntity> findByFromAccountIdOrderByCreatedAtDesc(UUID fromAccountId);
    List<TransactionEntity> findByToAccountIdOrderByCreatedAtDesc(UUID toAccountId);

    // Useful for monitoring — find transactions stuck in PROCESSING or COMPENSATING
    List<TransactionEntity> findByStatus(TransactionStatus status);
}
