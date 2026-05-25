package com.banking.domain.model;

// Currently only TRANSFER is used — DEPOSIT and WITHDRAWAL are here for future expansion.
public enum TransactionType {
    TRANSFER,
    DEPOSIT,
    WITHDRAWAL
}
