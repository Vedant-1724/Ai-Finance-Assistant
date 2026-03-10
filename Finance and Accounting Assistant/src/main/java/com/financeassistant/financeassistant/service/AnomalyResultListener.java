package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.entity.Anomaly;
import com.financeassistant.financeassistant.entity.Transaction;
import com.financeassistant.financeassistant.repository.AnomalyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true", matchIfMissing = true)
public class AnomalyResultListener {

    private static final Logger log = LoggerFactory.getLogger(AnomalyResultListener.class);

    @Autowired
    private AnomalyRepository anomalyRepository;

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

            List<Anomaly> savedAnomalies = new ArrayList<>();

            for (Map<String, Object> a : anomalyData) {
                Long txnId = a.get("id") != null
                        ? Long.valueOf(a.get("id").toString())
                        : null;

                BigDecimal amount = a.get("amount") != null
                        ? new BigDecimal(a.get("amount").toString())
                        : BigDecimal.ZERO;

                Anomaly anomaly = new Anomaly(companyId, txnId, amount, LocalDateTime.now());
                Anomaly saved = anomalyRepository.save(anomaly);
                savedAnomalies.add(saved);

                log.warn("  ↳ Anomaly saved — txnId={} amount={}", txnId, amount);
            }

            if (emailAlertService != null) {
                Transaction anomaly = (Transaction) payload.get("transaction");
                if (anomaly != null && anomaly.getUser() != null) {
                    emailAlertService.sendAnomalyAlert(
                            anomaly.getUser(),
                            anomaly.getAmount(),
                            anomaly.getDescription()
                    );
                }
            }

        } catch (Exception e) {
            log.error("Failed to process anomaly result: {}", e.getMessage(), e);
        }
    }
}
