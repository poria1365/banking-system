package com.banking.domain.port.in;

import com.banking.domain.model.Transaction;

import java.util.List;
import java.util.UUID;

// Inbound port for reading transactions — by ID or by account (merges from + to, newest first).
public interface GetTransactionUseCase {
    Transaction getTransaction(UUID transactionId);
    List<Transaction> getTransactionsByAccount(UUID accountId);
}
