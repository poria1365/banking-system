package com.banking.domain.port.in;

import com.banking.domain.model.Transaction;
import com.banking.domain.port.in.command.TransferMoneyCommand;

// Inbound port for initiating a money transfer.
// Idempotent: replaying the same idempotencyKey always returns the original transaction.
public interface TransferMoneyUseCase {
    Transaction transfer(TransferMoneyCommand command);
}
