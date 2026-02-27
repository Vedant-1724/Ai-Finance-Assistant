package com.financeassistant.financeassistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FIXES vs old finance-backend version:
 * 1. @Autowired(required = false) — app starts even when RabbitMQ is offline.
 *    Without this, Spring Boot crashes at startup if RabbitMQ is not running.
 * 2. Added publishNewTransaction(Long, Long) singular method — that's what
 *    TransactionService calls. Old version only had publishNewTransactions (plural).
 * 3. Wrapped publish in try-catch — anomaly detection is best-effort, it must
 *    NEVER crash the main transaction-save path.
 */
@Slf4j
@Service
public class TransactionEventPublisher {

    private static final String EXCHANGE    = "finance.exchange";
    private static final String ROUTING_KEY = "transactions.new";

    // required = false → app boots even without RabbitMQ running
    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    /**
     * Called by TransactionService after saving a single transaction.
     */
    public void publishNewTransaction(Long companyId, Long transactionId) {
        publishTransactionEvent(companyId, List.of(transactionId));
    }

    /**
     * Called for bulk imports (CSV, Plaid, etc.).
     */
    public void publishTransactionEvent(Long companyId, List<Long> txnIds) {
        if (rabbitTemplate == null) {
            log.debug("RabbitMQ not configured — skipping event for company={}", companyId);
            return;
        }
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("companyId", companyId);
            event.put("txnIds",    txnIds);

            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, event);
            log.info("Published {} txn(s) to RabbitMQ for company={}", txnIds.size(), companyId);

        } catch (Exception e) {
            // Non-critical — anomaly detection is async; never fail the save
            log.warn("RabbitMQ publish failed (non-critical): {}", e.getMessage());
        }
    }
}