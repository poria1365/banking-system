package com.banking.adapter.out.messaging;

import com.banking.domain.event.DomainEvent;
import com.banking.domain.port.out.EventPublisher;
import com.banking.infrastructure.annotation.PersistenceAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;

// Outbound adapter — serialises domain events to JSON and routes them to the RabbitMQ
// topic exchange. The routing key is derived from eventType (ACCOUNT_DEBITED → account.debited).
//
// Event publishing is intentionally non-fatal: the transaction is already committed by the time
// this runs. Failure here means an event is lost, not that money is lost.
// A production system should use the Transactional Outbox Pattern to guarantee delivery.
@PersistenceAdapter
@RequiredArgsConstructor
@Slf4j
public class RabbitMQEventPublisher implements EventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${banking.rabbitmq.exchange}")
    private String exchange;

    @Override
    public void publish(DomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            String routingKey = event.getEventType().toLowerCase().replace('_', '.');

            rabbitTemplate.convertAndSend(exchange, routingKey, payload);

            log.debug("Event published: type={} aggregateId={} routingKey={}",
                event.getEventType(), event.getAggregateId(), routingKey);

        } catch (AmqpException e) {
            log.error("Failed to publish event type={} aggregateId={}: {}",
                event.getEventType(), event.getAggregateId(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error publishing event type={}", event.getEventType(), e);
        }
    }
}
