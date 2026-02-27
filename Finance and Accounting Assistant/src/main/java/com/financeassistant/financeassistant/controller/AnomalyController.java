package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.entity.Anomaly;
import com.financeassistant.financeassistant.repository.AnomalyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * GAP 4 FIX: AnomalyRepository was saving anomalies to DB correctly via
 * AnomalyResultListener + RabbitMQ, but there was no HTTP endpoint for
 * the frontend to read them. This class closes that gap.
 *
 * Place at:
 *   src/main/java/com/financeassistant/financeassistant/controller/AnomalyController.java
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/{companyId}/anomalies")
@RequiredArgsConstructor
public class AnomalyController {

    private final AnomalyRepository anomalyRepository;

    /**
     * GET /api/v1/{companyId}/anomalies
     * Returns all anomaly records newest-first. Called by AnomalyPanel.tsx.
     */
    @GetMapping
    public ResponseEntity<List<Anomaly>> getAnomalies(@PathVariable Long companyId) {
        log.info("GET /anomalies companyId={}", companyId);
        List<Anomaly> anomalies =
                anomalyRepository.findByCompanyIdOrderByDetectedAtDesc(companyId);
        return ResponseEntity.ok(anomalies);
    }

    /**
     * DELETE /api/v1/{companyId}/anomalies/{anomalyId}
     * Dismiss/acknowledge an anomaly from the UI. Returns 204 or 404.
     */
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
