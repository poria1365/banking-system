package com.banking.domain.port.out;

import com.banking.domain.event.DomainEvent;

// Outbound port for publishing domain events. The domain doesn't know or care
// whether the real implementation sends to RabbitMQ, Kafka, or a test spy.
public interface EventPublisher {
    void publish(DomainEvent event);
}
