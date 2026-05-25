package com.banking.adapter.out.persistence;

import com.banking.adapter.out.persistence.entity.AccountEntity;
import com.banking.domain.model.Account;
import com.banking.domain.model.AccountStatus;
import com.banking.domain.model.Money;
import com.banking.domain.port.out.AccountRepository;
import com.banking.infrastructure.annotation.PersistenceAdapter;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Production AccountRepository backed by PostgreSQL via JPA.
 *
 * Optimistic locking strategy:
 *   Hibernate manages the @Version column on AccountEntity. On every UPDATE,
 *   it appends "AND version = ?" to the WHERE clause. If another transaction
 *   committed between our read and our write, Hibernate throws
 *   OptimisticLockException → Spring wraps it as OptimisticLockingFailureException.
 *   The caller (TransferMoneyService via distributed lock) should retry.
 *
 * This adapter is active only when the "prod" Spring profile is set.
 */
@PersistenceAdapter
@RequiredArgsConstructor
@Slf4j
public class JpaAccountRepositoryAdapter implements AccountRepository {

    private final AccountJpaRepository jpaRepository;

    @Override
    @Transactional
    public Account save(Account account) {
        AccountEntity entity = jpaRepository.findById(account.getId())
            .orElse(new AccountEntity());

        mapToEntity(account, entity);

        try {
            AccountEntity saved = jpaRepository.saveAndFlush(entity);
            log.debug("Account saved: id={} version={}", saved.getId(), saved.getVersion());
            return toDomain(saved);
        } catch (OptimisticLockException | OptimisticLockingFailureException ex) {
            log.warn("Optimistic lock conflict on account {}: {}", account.getId(), ex.getMessage());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Account> findById(UUID accountId) {
        return jpaRepository.findById(accountId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(UUID accountId) {
        return jpaRepository.existsById(accountId);
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private void mapToEntity(Account domain, AccountEntity entity) {
        entity.setId(domain.getId());
        entity.setOwnerId(domain.getOwnerId());
        entity.setBalance(domain.getBalance().getAmount());
        entity.setCurrency(domain.getCurrency());
        entity.setStatus(domain.getStatus());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        // entity.version is NOT set here — Hibernate owns it
    }

    private Account toDomain(AccountEntity entity) {
        return Account.reconstitute(
            entity.getId(),
            entity.getOwnerId(),
            Money.of(entity.getBalance(), entity.getCurrency()),
            entity.getCurrency(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getVersion() == null ? 0L : entity.getVersion()
        );
    }
}
