package com.banking.domain.model;

import com.banking.domain.exception.AccountInactiveException;
import com.banking.domain.exception.InsufficientFundsException;

import java.time.Instant;
import java.util.UUID;

// Core aggregate — owns all business rules around balances and account state.
// No Spring, no JPA, no nothing — just plain Java. Keeps the domain honest.
public class Account {

    private final UUID id;
    private final String ownerId;
    private Money balance;
    private final String currency;
    private AccountStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version; // mirrors the DB @Version column for optimistic locking

    private Account(UUID id, String ownerId, Money balance, String currency,
                    AccountStatus status, Instant createdAt, Instant updatedAt, long version) {
        this.id = id;
        this.ownerId = ownerId;
        this.balance = balance;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    // Use this when opening a brand-new account
    public static Account create(String ownerId, Money initialBalance) {
        Instant now = Instant.now();
        return new Account(
            UUID.randomUUID(),
            ownerId,
            initialBalance,
            initialBalance.getCurrency(),
            AccountStatus.ACTIVE,
            now,
            now,
            0L
        );
    }

    // Use this when loading an account back from the DB
    public static Account reconstitute(UUID id, String ownerId, Money balance, String currency,
                                       AccountStatus status, Instant createdAt,
                                       Instant updatedAt, long version) {
        return new Account(id, ownerId, balance, currency, status, createdAt, updatedAt, version);
    }

    // Checks active status AND sufficient funds before touching the balance
    public void debit(Money amount) {
        requireActive();
        requireSufficientFunds(amount);
        this.balance = this.balance.subtract(amount);
        touch();
    }

    // Only checks active — you can credit a zero-balance account
    public void credit(Money amount) {
        requireActive();
        this.balance = this.balance.add(amount);
        touch();
    }

    public void freeze() {
        if (this.status == AccountStatus.CLOSED) {
            throw new AccountInactiveException("Cannot freeze a closed account: " + id);
        }
        this.status = AccountStatus.FROZEN;
        touch();
    }

    public void close() {
        this.status = AccountStatus.CLOSED;
        touch();
    }

    public boolean isActive() {
        return this.status == AccountStatus.ACTIVE;
    }

    private void requireActive() {
        if (this.status != AccountStatus.ACTIVE) {
            throw new AccountInactiveException("Account " + id + " is not active. Status: " + status);
        }
    }

    private void requireSufficientFunds(Money amount) {
        if (this.balance.isLessThan(amount)) {
            throw new InsufficientFundsException(id, this.balance, amount);
        }
    }

    // Every mutation bumps updatedAt and version — version is synced to DB on save
    private void touch() {
        this.updatedAt = Instant.now();
        this.version++;
    }

    public UUID getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public Money getBalance() { return balance; }
    public String getCurrency() { return currency; }
    public AccountStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
