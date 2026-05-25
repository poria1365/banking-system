package com.banking.domain.event;

import com.banking.domain.model.Money;

import java.util.UUID;

// Fired after the source account balance is reduced and persisted.
// If you see this event but no AccountCreditedEvent, compensation should follow.
public class AccountDebitedEvent extends DomainEvent {

    private final UUID transactionId;
    private final Money amount;

    public AccountDebitedEvent(UUID transactionId, UUID accountId, Money amount) {
        super("ACCOUNT_DEBITED", accountId);
        this.transactionId = transactionId;
        this.amount = amount;
    }

    public UUID getTransactionId() { return transactionId; }
    public UUID getAccountId() { return getAggregateId(); }
    public Money getAmount() { return amount; }
}
