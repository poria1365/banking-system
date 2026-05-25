package com.banking.domain.event;

import com.banking.domain.model.Money;

import java.util.UUID;

// Fired as soon as the saga starts — before any balance changes.
// Useful for audit logs and tracing the full journey of a transfer.
public class TransferInitiatedEvent extends DomainEvent {

    private final UUID fromAccountId;
    private final UUID toAccountId;
    private final Money amount;

    public TransferInitiatedEvent(UUID transactionId, UUID fromAccountId,
                                   UUID toAccountId, Money amount) {
        super("TRANSFER_INITIATED", transactionId);
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
    }

    public UUID getFromAccountId() { return fromAccountId; }
    public UUID getToAccountId() { return toAccountId; }
    public Money getAmount() { return amount; }
}
