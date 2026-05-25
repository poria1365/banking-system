package com.banking.domain.port.in.command;

import com.banking.domain.model.Money;

import java.util.UUID;

// Transfer intent. Validated at construction so the service never sees garbage.
// The same-account check here prevents self-transfers before any DB round trips.
public record TransferMoneyCommand(
    String idempotencyKey,
    UUID fromAccountId,
    UUID toAccountId,
    Money amount
) {
    public TransferMoneyCommand {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        if (fromAccountId == null || toAccountId == null) {
            throw new IllegalArgumentException("Account IDs must not be null");
        }
        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("Source and destination accounts must differ");
        }
        if (amount == null || amount.isZero()) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
    }
}
