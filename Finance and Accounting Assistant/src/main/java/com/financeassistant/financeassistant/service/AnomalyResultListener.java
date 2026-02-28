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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AnomalyResultListener — UPDATED for Email Alerts
 *
 * Changes from previous version:
 *   1. Collects all saved Anomaly entities into a list.
 *   2. After saving ALL anomalies, calls emailAlertService.sendAnomalyAlert()
 *      once per message (not per anomaly) — one email summarising all detections.
 *   3. emailAlertService is @Autowired(required = false) so this class still
 *      works even if mail is not configured.
 *
 * Flow:
 *   Python → RabbitMQ (ai.anomaly.results)
 *       → onAnomalyResult()
 *           → save to DB
 *           → sendAnomalyAlert() [async — non-blocking]
 *
 * Place at:
 *   Finance and Accounting Assistant/src/main/java/com/financeassistant/
 *   financeassistant/service/AnomalyResultListener.java
 */
@Service
public class AnomalyResultListener {

    private static final Logger log = LoggerFactory.getLogger(AnomalyResultListener.class);

    @Autowired
    private AnomalyRepository anomalyRepository;

    // required = false → works even without mail configured
    @Autowired(required = false)
    private EmailAlertService emailAlertService;

    @RabbitListener(queues = "ai.anomaly.results")
    public void onAnomalyResult(Map<String, Object> payload) {
        try {
            Long companyId = Long.valueOf(payload.get("companyId").toString());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> anomalyData =
                    (List<Map<String, Object>>) payload.get("anomalies");

            if (anomalyData == null || anomalyData.isEmpty()) {
                log.info("No anomalies detected for company {}", companyId);
                return;
            }

            log.warn("Received {} anomaly/anomalies for company {}", anomalyData.size(), companyId);

            // ── Save all anomalies and collect entities ────────────────────────
            List<Anomaly> savedAnomalies = new ArrayList<>();

            for (Map<String, Object> a : anomalyData) {
                Long txnId = a.get("id") != null
                        ? Long.valueOf(a.get("id").toString())
                        : null;

                BigDecimal amount = a.get("amount") != null
                        ? new BigDecimal(a.get("amount").toString())
                        : BigDecimal.ZERO;

                Anomaly anomaly = new Anomaly(companyId, txnId, amount, LocalDateTime.now());
                Anomaly saved  = anomalyRepository.save(anomaly);
                savedAnomalies.add(saved);

                log.warn("  ↳ Anomaly saved — txnId={} amount={}", txnId, amount);
            }

            // ── Send one email summarising all detected anomalies ──────────────
            // Runs async — never blocks the consumer thread
            if (emailAlertService != null) {
                emailAlertService.sendAnomalyAlert(companyId, savedAnomalies);
            }

        } catch (Exception e) {
            log.error("Failed to process anomaly result: {}", e.getMessage(), e);
        }
    }
}
