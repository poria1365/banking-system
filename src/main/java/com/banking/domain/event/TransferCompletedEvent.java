package com.banking.domain.event;

import java.util.UUID;

// Happy path end — everything went through, transaction is COMPLETED.
public class TransferCompletedEvent extends DomainEvent {

    public TransferCompletedEvent(UUID transactionId) {
        super("TRANSFER_COMPLETED", transactionId);
    }

    public UUID getTransactionId() { return getAggregateId(); }
}
