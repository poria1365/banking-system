package com.banking.domain.exception;

// Thrown when you try to debit or credit a FROZEN or CLOSED account. Maps to 422.
public class AccountInactiveException extends DomainException {

    public AccountInactiveException(String message) {
        super(message);
    }
}
