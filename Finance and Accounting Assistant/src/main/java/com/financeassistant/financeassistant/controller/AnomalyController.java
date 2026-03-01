package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.entity.Anomaly;
import com.financeassistant.financeassistant.repository.AnomalyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/
 *       financeassistant/controller/AnomalyController.java
 *
 * CHANGE: @PreAuthorize added to both endpoints.
 * Without this, any authenticated user could:
 *   - Read another company's anomaly alerts (data leak)
 *   - Dismiss another company's anomalies (data tampering)
 *
 * Exposes HTTP endpoints so the React Dashboard can display anomaly alerts.
 * AnomalyResultListener saves anomalies to the DB via RabbitMQ;
 * this controller lets the authenticated owner READ and DISMISS them.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/{companyId}/anomalies")
@RequiredArgsConstructor
public class AnomalyController {

    private final AnomalyRepository anomalyRepository;

    /**
     * GET /api/v1/{companyId}/anomalies
     * Returns all anomalies for this company, newest first.
     */
    @GetMapping
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<List<Anomaly>> getAnomalies(@PathVariable Long companyId) {
        log.info("GET /anomalies companyId={}", companyId);
        return ResponseEntity.ok(
                anomalyRepository.findByCompanyIdOrderByDetectedAtDesc(companyId));
    }

    /**
     * DELETE /api/v1/{companyId}/anomalies/{anomalyId}
     * Dismisses (hard-deletes) a single anomaly alert.
     */
    @DeleteMapping("/{anomalyId}")
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<Void> dismissAnomaly(
            @PathVariable Long companyId,
            @PathVariable Long anomalyId) {
        log.info("DELETE /anomalies/{} companyId={}", anomalyId, companyId);

        // Extra guard: verify the anomaly actually belongs to this company
        anomalyRepository.findById(anomalyId).ifPresent(a -> {
            if (a.getCompanyId().equals(companyId)) {
                anomalyRepository.deleteById(anomalyId);
            }
        });

        return ResponseEntity.noContent().build();
    }
}
