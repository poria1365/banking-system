package com.banking.domain.exception;

// Thrown when idempotency check finds an existing record for the same key. Maps to 409.
// In practice TransferMoneyService returns the existing transaction instead of throwing this,
// but it's here for cases where a true duplicate conflict needs to surface explicitly.
public class DuplicateTransactionException extends DomainException {

    public DuplicateTransactionException(String idempotencyKey) {
        super("Transaction with idempotency key already processed: " + idempotencyKey);
    }
}
