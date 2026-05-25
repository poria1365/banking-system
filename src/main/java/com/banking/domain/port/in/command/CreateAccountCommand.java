package com.banking.domain.port.in.command;

import com.banking.domain.model.Money;

// Carries validated input from the web layer into the application layer.
// Compact constructor runs on every instantiation — catches bad data at the boundary.
public record CreateAccountCommand(
    String ownerId,
    Money initialBalance
) {
    public CreateAccountCommand {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        if (initialBalance == null) {
            throw new IllegalArgumentException("initialBalance must not be null");
        }
    }
}
