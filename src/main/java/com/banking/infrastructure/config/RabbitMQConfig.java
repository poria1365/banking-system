package com.banking.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Declares the full RabbitMQ topology: one topic exchange, six durable queues, and their bindings.
// The transfer-initiated queue has a Dead Letter Exchange so rejected messages land in banking.dead-letter
// instead of being silently dropped.
@Configuration
public class RabbitMQConfig {

    @Value("${banking.rabbitmq.exchange}")
    private String exchange;

    @Value("${banking.rabbitmq.queues.transfer-initiated}")
    private String transferInitiatedQueue;

    @Value("${banking.rabbitmq.queues.account-debited}")
    private String accountDebitedQueue;

    @Value("${banking.rabbitmq.queues.account-credited}")
    private String accountCreditedQueue;

    @Value("${banking.rabbitmq.queues.transfer-completed}")
    private String transferCompletedQueue;

    @Value("${banking.rabbitmq.queues.transfer-failed}")
    private String transferFailedQueue;

    @Value("${banking.rabbitmq.queues.dead-letter}")
    private String deadLetterQueue;

    @Bean
    TopicExchange bankingExchange() {
        return ExchangeBuilder.topicExchange(exchange).durable(true).build();
    }

    // DLX wired so poison messages don't block the queue
    @Bean
    Queue transferInitiatedQueue() {
        return QueueBuilder.durable(transferInitiatedQueue)
            .withArgument("x-dead-letter-exchange", exchange)
            .withArgument("x-dead-letter-routing-key", "dead-letter")
            .build();
    }

    @Bean Queue accountDebitedQueue()   { return QueueBuilder.durable(accountDebitedQueue).build(); }
    @Bean Queue accountCreditedQueue()  { return QueueBuilder.durable(accountCreditedQueue).build(); }
    @Bean Queue transferCompletedQueue(){ return QueueBuilder.durable(transferCompletedQueue).build(); }
    @Bean Queue transferFailedQueue()   { return QueueBuilder.durable(transferFailedQueue).build(); }
    @Bean Queue deadLetterQueue()       { return QueueBuilder.durable(deadLetterQueue).build(); }

    @Bean Binding bindTransferInitiated() {
        return BindingBuilder.bind(transferInitiatedQueue()).to(bankingExchange()).with("transfer.initiated");
    }
    @Bean Binding bindAccountDebited() {
        return BindingBuilder.bind(accountDebitedQueue()).to(bankingExchange()).with("account.debited");
    }
    @Bean Binding bindAccountCredited() {
        return BindingBuilder.bind(accountCreditedQueue()).to(bankingExchange()).with("account.credited");
    }
    @Bean Binding bindTransferCompleted() {
        return BindingBuilder.bind(transferCompletedQueue()).to(bankingExchange()).with("transfer.completed");
    }
    @Bean Binding bindTransferFailed() {
        return BindingBuilder.bind(transferFailedQueue()).to(bankingExchange()).with("transfer.failed");
    }
    @Bean Binding bindDeadLetter() {
        return BindingBuilder.bind(deadLetterQueue()).to(bankingExchange()).with("dead-letter");
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        template.setMandatory(true); // throws if no queue is bound for the routing key
        return template;
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setDefaultRequeueRejected(false); // failed messages go to DLQ, not back to queue
        return factory;
    }
}
