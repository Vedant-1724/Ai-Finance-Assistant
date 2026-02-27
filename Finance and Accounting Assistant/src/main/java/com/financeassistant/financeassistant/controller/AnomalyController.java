package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.entity.Anomaly;
import com.financeassistant.financeassistant.repository.AnomalyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * NEW FILE — did not exist in finance-backend.
 *
 * Exposes HTTP endpoints so the React Dashboard can display anomaly alerts.
 * AnomalyResultListener saves anomalies to the DB via RabbitMQ;
 * this controller lets the frontend READ them.
 *
 * Endpoints:
 *   GET    /api/v1/{companyId}/anomalies            → list all anomalies
 *   DELETE /api/v1/{companyId}/anomalies/{anomalyId} → dismiss one
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/{companyId}/anomalies")
@RequiredArgsConstructor
public class AnomalyController {

    private final AnomalyRepository anomalyRepository;

    @GetMapping
    public ResponseEntity<List<Anomaly>> getAnomalies(@PathVariable Long companyId) {
        log.info("GET /anomalies companyId={}", companyId);
        List<Anomaly> anomalies =
                anomalyRepository.findByCompanyIdOrderByDetectedAtDesc(companyId);
        return ResponseEntity.ok(anomalies);
    }

    @DeleteMapping("/{anomalyId}")
    public ResponseEntity<Void> dismissAnomaly(
            @PathVariable Long companyId,
            @PathVariable Long anomalyId) {

        log.info("DELETE /anomalies/{} companyId={}", anomalyId, companyId);
        if (!anomalyRepository.existsById(anomalyId)) {
            return ResponseEntity.notFound().build();
        }
        anomalyRepository.deleteById(anomalyId);
        return ResponseEntity.noContent().build();
    }
}