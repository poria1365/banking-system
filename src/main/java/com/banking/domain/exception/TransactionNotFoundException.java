package com.banking.domain.exception;

import java.util.UUID;

// Thrown when a lookup by transaction ID finds nothing. Maps to 404.
public class TransactionNotFoundException extends DomainException {

    public TransactionNotFoundException(UUID transactionId) {
        super("Transaction not found: " + transactionId);
    }
}
