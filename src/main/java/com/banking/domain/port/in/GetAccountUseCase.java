package com.banking.domain.port.in;

import com.banking.domain.model.Account;

import java.util.UUID;

// Inbound port for fetching a single account by ID. Throws AccountNotFoundException if missing.
public interface GetAccountUseCase {
    Account getAccount(UUID accountId);
}
