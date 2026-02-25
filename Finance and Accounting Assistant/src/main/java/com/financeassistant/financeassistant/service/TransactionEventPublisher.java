package com.financeassistant.financeassistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class TransactionEventPublisher {

    private static final String EXCHANGE    = "finance.exchange";
    private static final String ROUTING_KEY = "transactions.new";

    // Optional injection — null when RabbitMQ auto-config is excluded
    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    /**
     * Publish a single new transaction event.
     * Called by TransactionService after saving.
     */
    public void publishNewTransaction(Long companyId, Long transactionId) {
        publishTransactionEvent(companyId, List.of(transactionId));
    }

    /**
     * Publish multiple transaction IDs (e.g. after CSV import).
     * Silently skipped if RabbitMQ is not configured.
     */
    public void publishTransactionEvent(Long companyId, List<Long> txnIds) {
        if (rabbitTemplate == null) {
            log.debug("RabbitMQ not configured — skipping event publish for company={}", companyId);
            return;
        }

        try {
            Map<String, Object> event = new HashMap<>();
            event.put("companyId", companyId);
            event.put("txnIds",    txnIds);

            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, event);
            log.info("Published {} transaction(s) to RabbitMQ for company={}", txnIds.size(), companyId);

        } catch (Exception e) {
            // Non-critical — anomaly detection is async and best-effort
            log.warn("RabbitMQ publish failed (non-critical): {}", e.getMessage());
        }
    }
}