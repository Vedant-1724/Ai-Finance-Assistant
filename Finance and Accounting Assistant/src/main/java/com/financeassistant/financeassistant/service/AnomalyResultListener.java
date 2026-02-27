package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.entity.Anomaly;
import com.financeassistant.financeassistant.repository.AnomalyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * NEW FILE — did not exist in finance-backend.
 *
 * This is the "close the RabbitMQ loop" component (Feature #6 in your roadmap).
 *
 * Flow:
 *   1. TransactionService saves a transaction and publishes to "ai.anomaly.queue"
 *      via TransactionEventPublisher.
 *   2. Python rabbitmq_consumer.py picks it up, runs IsolationForest, and
 *      publishes results to "ai.anomaly.results".
 *   3. THIS class listens on "ai.anomaly.results" and saves flagged anomalies
 *      to the PostgreSQL 'anomalies' table.
 *   4. AnomalyController exposes GET /api/v1/{companyId}/anomalies so the
 *      React Dashboard.tsx can poll and display them.
 */
@Service
public class AnomalyResultListener {

    private static final Logger log = LoggerFactory.getLogger(AnomalyResultListener.class);

    @Autowired
    private AnomalyRepository anomalyRepository;

    @RabbitListener(queues = "ai.anomaly.results")
    public void onAnomalyResult(Map<String, Object> payload) {
        try {
            Long companyId = Long.valueOf(payload.get("companyId").toString());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> anomalies =
                    (List<Map<String, Object>>) payload.get("anomalies");

            if (anomalies == null || anomalies.isEmpty()) {
                log.info("No anomalies detected for company {}", companyId);
                return;
            }

            log.warn("Received {} anomaly/anomalies for company {}", anomalies.size(), companyId);

            for (Map<String, Object> a : anomalies) {
                Long txnId = a.get("id") != null
                        ? Long.valueOf(a.get("id").toString())
                        : null;

                BigDecimal amount = a.get("amount") != null
                        ? new BigDecimal(a.get("amount").toString())
                        : BigDecimal.ZERO;

                Anomaly anomaly = new Anomaly(companyId, txnId, amount, LocalDateTime.now());
                anomalyRepository.save(anomaly);

                log.warn("  ↳ Anomaly saved — txnId={} amount={}", txnId, amount);
            }

        } catch (Exception e) {
            log.error("Failed to process anomaly result: {}", e.getMessage(), e);
        }
    }
}