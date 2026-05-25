package com.banking.domain.event;

import java.time.Instant;
import java.util.UUID;

// Base class for all domain events. Every event gets a unique ID and timestamp at creation.
// aggregateId ties the event back to the entity it happened on (account or transaction).
public abstract class DomainEvent {

    private final UUID eventId;
    private final String eventType;
    private final Instant occurredAt;
    private final UUID aggregateId;

    protected DomainEvent(String eventType, UUID aggregateId) {
        this.eventId = UUID.randomUUID();
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.occurredAt = Instant.now();
    }

    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public Instant getOccurredAt() { return occurredAt; }
    public UUID getAggregateId() { return aggregateId; }
}
