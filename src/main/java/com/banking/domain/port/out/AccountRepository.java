package com.banking.domain.port.out;

import com.banking.domain.model.Account;

import java.util.Optional;
import java.util.UUID;

// Outbound port — the domain's view of how accounts are persisted.
// The actual implementation (JPA + PostgreSQL) lives in the adapter layer.
public interface AccountRepository {
    Account save(Account account);
    Optional<Account> findById(UUID accountId);
    boolean existsById(UUID accountId);
}
