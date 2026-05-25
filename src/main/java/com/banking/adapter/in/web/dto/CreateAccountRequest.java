package com.banking.adapter.in.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// Request body for POST /api/v1/accounts.
// Bean Validation runs before the controller method fires, so bad input never reaches the service.
public record CreateAccountRequest(
    @NotBlank(message = "ownerId is required")
    @Size(max = 100)
    String ownerId,

    @NotNull(message = "initialBalance is required")
    @DecimalMin(value = "0.00", message = "initialBalance must be >= 0")
    BigDecimal initialBalance,

    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code")
    String currency
) {}
