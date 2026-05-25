package com.banking.adapter.in.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

// Request body for POST /api/v1/transactions/transfer.
// idempotencyKey is client-generated — use UUID or a hash of the intent, max 64 chars.
public record TransferRequest(
    @NotBlank(message = "idempotencyKey is required")
    @Size(max = 64)
    String idempotencyKey,

    @NotNull(message = "fromAccountId is required")
    UUID fromAccountId,

    @NotNull(message = "toAccountId is required")
    UUID toAccountId,

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be > 0")
    BigDecimal amount,

    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3)
    String currency
) {}
