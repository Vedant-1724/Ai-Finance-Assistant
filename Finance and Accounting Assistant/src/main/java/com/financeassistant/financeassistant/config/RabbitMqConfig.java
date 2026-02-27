package com.financeassistant.financeassistant.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    // ── Shared Exchange ───────────────────────────────────────────────────────
    @Bean
    public TopicExchange financeExchange() {
        return new TopicExchange("finance.exchange");
    }

    // ── Outbound: Spring Boot → Python (transactions.new) ─────────────────────
    @Bean
    public Queue anomalyQueue() {
        return QueueBuilder.durable("ai.anomaly.queue").build();
    }

    @Bean
    public Binding anomalyBinding(Queue anomalyQueue, TopicExchange financeExchange) {
        return BindingBuilder.bind(anomalyQueue)
                .to(financeExchange)
                .with("transactions.new");
    }

    // ── Inbound: Python → Spring Boot (anomalies.detected) ────────────────────
    @Bean
    public Queue anomalyResultsQueue() {
        return QueueBuilder.durable("ai.anomaly.results").build();
    }

    @Bean
    public Binding anomalyResultsBinding(Queue anomalyResultsQueue,
                                         TopicExchange financeExchange) {
        return BindingBuilder.bind(anomalyResultsQueue)
                .to(financeExchange)
                .with("anomalies.detected");
    }

    // ── JSON Serialization (required for Python interop) ──────────────────────
    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        return factory;
    }
}