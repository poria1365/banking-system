package com.banking.adapter.in.web.dto;

import com.banking.domain.model.Account;
import com.banking.domain.model.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Read model for the API — maps from the domain Account to a flat JSON-friendly shape.
// We expose BigDecimal directly so Jackson serialises it as a number, not a string.
public record AccountResponse(
    UUID id,
    String ownerId,
    BigDecimal balance,
    String currency,
    AccountStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
            account.getId(),
            account.getOwnerId(),
            account.getBalance().getAmount(),
            account.getCurrency(),
            account.getStatus(),
            account.getCreatedAt(),
            account.getUpdatedAt()
        );
    }
}
