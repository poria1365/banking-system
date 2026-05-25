package com.banking.domain.port.out;

import com.banking.domain.model.Transaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Outbound port for transaction persistence.
// findByIdempotencyKey is the application-level deduplication check — the DB UNIQUE constraint
// is the last-resort guard if two requests slip through simultaneously.
public interface TransactionRepository {
    Transaction save(Transaction transaction);
    Optional<Transaction> findById(UUID transactionId);
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
    List<Transaction> findByFromAccountId(UUID accountId);
    List<Transaction> findByToAccountId(UUID accountId);
}
