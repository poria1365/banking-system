package com.banking.adapter.out.persistence;

import com.banking.adapter.out.persistence.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

// Spring Data JPA repository for the accounts table.
// findByIdWithLock issues a SELECT FOR UPDATE — a DB-level fallback if the Redis lock
// can't be acquired. In normal flow the Redisson lock handles concurrency.
public interface AccountJpaRepository extends JpaRepository<AccountEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountEntity a WHERE a.id = :id")
    Optional<AccountEntity> findByIdWithLock(@Param("id") UUID id);
}
