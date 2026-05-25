package com.banking.application.service;

import com.banking.domain.exception.AccountNotFoundException;
import com.banking.domain.model.Account;
import com.banking.domain.port.in.GetAccountUseCase;
import com.banking.domain.port.out.AccountRepository;
import com.banking.infrastructure.annotation.UseCase;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

// Simple read use case — just a fetch with a typed 404 if nothing is found.
@UseCase
@RequiredArgsConstructor
public class GetAccountService implements GetAccountUseCase {

    private final AccountRepository accountRepository;

    @Override
    public Account getAccount(UUID accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
