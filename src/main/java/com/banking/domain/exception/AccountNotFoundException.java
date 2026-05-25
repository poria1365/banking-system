package com.banking.domain.exception;

import java.util.UUID;

// Thrown when a lookup by account ID finds nothing. Maps to 404.
public class AccountNotFoundException extends DomainException {

    public AccountNotFoundException(UUID accountId) {
        super("Account not found: " + accountId);
    }
}
