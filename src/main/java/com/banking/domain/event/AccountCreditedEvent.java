package com.banking.domain.event;

import com.banking.domain.model.Money;

import java.util.UUID;

// Fired after the target account balance is increased and persisted.
// At this point both accounts are updated — only the status record write remains.
public class AccountCreditedEvent extends DomainEvent {

    private final UUID transactionId;
    private final Money amount;

    public AccountCreditedEvent(UUID transactionId, UUID accountId, Money amount) {
        super("ACCOUNT_CREDITED", accountId);
        this.transactionId = transactionId;
        this.amount = amount;
    }

    public UUID getTransactionId() { return transactionId; }
    public UUID getAccountId() { return getAggregateId(); }
    public Money getAmount() { return amount; }
}
