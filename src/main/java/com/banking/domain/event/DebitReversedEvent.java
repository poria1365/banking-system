package com.banking.domain.event;

import com.banking.domain.model.Money;

import java.util.UUID;

// Compensation succeeded — the source account has been credited back.
// Downstream consumers can use this to update balances in read models.
public class DebitReversedEvent extends DomainEvent {

    private final UUID transactionId;
    private final Money amount;

    public DebitReversedEvent(UUID transactionId, UUID accountId, Money amount) {
        super("DEBIT_REVERSED", accountId);
        this.transactionId = transactionId;
        this.amount = amount;
    }

    public UUID getTransactionId() { return transactionId; }
    public UUID getAccountId() { return getAggregateId(); }
    public Money getAmount() { return amount; }
}
