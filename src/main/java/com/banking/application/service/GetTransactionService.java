package com.banking.application.service;

import com.banking.domain.exception.TransactionNotFoundException;
import com.banking.domain.model.Transaction;
import com.banking.domain.port.in.GetTransactionUseCase;
import com.banking.domain.port.out.TransactionRepository;
import com.banking.infrastructure.annotation.UseCase;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Handles both single-transaction lookup and full account history.
// Account history merges from+to queries then sorts newest-first — an account appears
// in both the "sent" and "received" lists, so we combine them here in memory.
@UseCase
@RequiredArgsConstructor
public class GetTransactionService implements GetTransactionUseCase {

    private final TransactionRepository transactionRepository;

    @Override
    public Transaction getTransaction(UUID transactionId) {
        return transactionRepository.findById(transactionId)
            .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    @Override
    public List<Transaction> getTransactionsByAccount(UUID accountId) {
        List<Transaction> all = new ArrayList<>();
        all.addAll(transactionRepository.findByFromAccountId(accountId));
        all.addAll(transactionRepository.findByToAccountId(accountId));
        // Newest first — standard for account history views
        all.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return all;
    }
}
