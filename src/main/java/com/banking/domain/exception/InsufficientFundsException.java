package com.banking.domain.exception;

import com.banking.domain.model.Money;

import java.util.UUID;

// Thrown during debit when the available balance is less than the requested amount. Maps to 422.
// Includes both amounts in the message so the caller knows exactly how short they are.
public class InsufficientFundsException extends DomainException {

    public InsufficientFundsException(UUID accountId, Money available, Money required) {
        super(String.format("Insufficient funds in account %s: available=%s, required=%s",
            accountId, available, required));
    }
}
