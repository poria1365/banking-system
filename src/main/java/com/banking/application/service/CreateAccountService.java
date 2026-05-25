package com.banking.application.service;

import com.banking.domain.model.Account;
import com.banking.domain.port.in.CreateAccountUseCase;
import com.banking.domain.port.in.command.CreateAccountCommand;
import com.banking.domain.port.out.AccountRepository;
import com.banking.infrastructure.annotation.UseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Thin use case — creates the account aggregate and persists it. No business logic here,
// that all lives in Account.create(). This just orchestrates the call.
@UseCase
@RequiredArgsConstructor
@Slf4j
public class CreateAccountService implements CreateAccountUseCase {

    private final AccountRepository accountRepository;

    @Override
    public Account createAccount(CreateAccountCommand command) {
        log.info("Creating account for owner={} with initial balance={}",
            command.ownerId(), command.initialBalance());

        Account account = Account.create(command.ownerId(), command.initialBalance());
        Account saved = accountRepository.save(account);

        log.info("Account created: id={}", saved.getId());
        return saved;
    }
}
