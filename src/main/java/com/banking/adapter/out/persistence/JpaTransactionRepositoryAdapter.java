package com.banking.adapter.out.persistence;

import com.banking.adapter.out.persistence.entity.TransactionEntity;
import com.banking.domain.model.Money;
import com.banking.domain.model.Transaction;
import com.banking.domain.port.out.TransactionRepository;
import com.banking.infrastructure.annotation.PersistenceAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Production TransactionRepository backed by PostgreSQL via JPA.
 *
 * The UNIQUE constraint on idempotency_key provides a DB-level safety net:
 * even if two concurrent requests both pass the application-level idempotency
 * check simultaneously, the DB constraint guarantees only one INSERT succeeds.
 * The loser gets a DataIntegrityViolationException, which the service maps
 * to the existing transaction (idempotent response).
 *
 * This adapter is active only when the "prod" Spring profile is set.
 */
@PersistenceAdapter
@RequiredArgsConstructor
@Slf4j
public class JpaTransactionRepositoryAdapter implements TransactionRepository {

    private final TransactionJpaRepository jpaRepository;

    @Override
    @Transactional
    public Transaction save(Transaction transaction) {
        TransactionEntity entity = jpaRepository.findById(transaction.getId())
            .orElse(new TransactionEntity());

        mapToEntity(transaction, entity);

        try {
            TransactionEntity saved = jpaRepository.saveAndFlush(entity);
            log.debug("Transaction saved: id={} status={}", saved.getId(), saved.getStatus());
            return toDomain(saved);
        } catch (DataIntegrityViolationException ex) {
            // Idempotency key unique constraint violated — concurrent duplicate request
            log.warn("Duplicate idempotency key detected: {}", transaction.getIdempotencyKey());
            return jpaRepository.findByIdempotencyKey(transaction.getIdempotencyKey())
                .map(this::toDomain)
                .orElseThrow(() -> ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Transaction> findById(UUID transactionId) {
        return jpaRepository.findById(transactionId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Transaction> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> findByFromAccountId(UUID accountId) {
        return jpaRepository.findByFromAccountIdOrderByCreatedAtDesc(accountId)
            .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> findByToAccountId(UUID accountId) {
        return jpaRepository.findByToAccountIdOrderByCreatedAtDesc(accountId)
            .stream().map(this::toDomain).collect(Collectors.toList());
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private void mapToEntity(Transaction domain, TransactionEntity entity) {
        entity.setId(domain.getId());
        entity.setIdempotencyKey(domain.getIdempotencyKey());
        entity.setFromAccountId(domain.getFromAccountId());
        entity.setToAccountId(domain.getToAccountId());
        entity.setAmount(domain.getAmount().getAmount());
        entity.setCurrency(domain.getAmount().getCurrency());
        entity.setType(domain.getType());
        entity.setStatus(domain.getStatus());
        entity.setFailureReason(domain.getFailureReason());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setCompletedAt(domain.getCompletedAt());
    }

    private Transaction toDomain(TransactionEntity entity) {
        return Transaction.reconstitute(
            entity.getId(),
            entity.getIdempotencyKey(),
            entity.getFromAccountId(),
            entity.getToAccountId(),
            Money.of(entity.getAmount(), entity.getCurrency()),
            entity.getType(),
            entity.getStatus(),
            entity.getFailureReason(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getCompletedAt()
        );
    }
}
