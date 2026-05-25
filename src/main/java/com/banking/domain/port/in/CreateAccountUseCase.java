package com.banking.domain.port.in;

import com.banking.domain.model.Account;
import com.banking.domain.port.in.command.CreateAccountCommand;

// Inbound port — the domain's contract for opening a new account.
// Controllers call this; they never touch the service class directly.
public interface CreateAccountUseCase {
    Account createAccount(CreateAccountCommand command);
}
