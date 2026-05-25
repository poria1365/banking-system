package com.banking.domain.event;

import java.util.UUID;

// Published on any terminal failure — whether no money moved (FAILED),
// debit was reversed (COMPENSATED), or manual review is needed.
public class TransferFailedEvent extends DomainEvent {

    private final String reason;

    public TransferFailedEvent(UUID transactionId, String reason) {
        super("TRANSFER_FAILED", transactionId);
        this.reason = reason;
    }

    public UUID getTransactionId() { return getAggregateId(); }
    public String getReason() { return reason; }
}
