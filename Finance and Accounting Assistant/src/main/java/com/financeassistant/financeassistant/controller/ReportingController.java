package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.dto.PnLReport;
import com.financeassistant.financeassistant.service.ReportingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/{companyId}/reports")
@RequiredArgsConstructor
public class ReportingController {

    private final ReportingService reportingService;

    /**
     * GET /api/v1/{companyId}/reports/pnl?period=month
     *
     * Supported period values:
     *   month   → current calendar month
     *   quarter → current calendar quarter (Q1/Q2/Q3/Q4)
     *   year    → current calendar year
     *   2026-02 → specific month in YYYY-MM format
     *
     * Response is cached in Redis. Cache auto-evicts when a new
     * transaction is saved via TransactionService.
     */
    @GetMapping("/pnl")
    public ResponseEntity<PnLReport> getPnLReport(
            @PathVariable Long companyId,
            @RequestParam(defaultValue = "month") String period) {

        log.info("GET /reports/pnl companyId={} period={}", companyId, period);
        PnLReport report = reportingService.getPnLReport(companyId, period);
        return ResponseEntity.ok(report);
    }

    /**
     * GET /api/v1/{companyId}/reports/summary
     *
     * Returns all three period reports (month, quarter, year)
     * in one call — useful for a full dashboard summary page.
     */
    @GetMapping("/summary")
    public ResponseEntity<ReportingSummary> getSummary(@PathVariable Long companyId) {
        log.info("GET /reports/summary companyId={}", companyId);

        PnLReport monthly   = reportingService.getPnLReport(companyId, "month");
        PnLReport quarterly = reportingService.getPnLReport(companyId, "quarter");
        PnLReport yearly    = reportingService.getPnLReport(companyId, "year");

        return ResponseEntity.ok(new ReportingSummary(monthly, quarterly, yearly));
    }

    // ── Inner response record ─────────────────────────────────────────────────
    public record ReportingSummary(
            PnLReport monthly,
            PnLReport quarterly,
            PnLReport yearly
    ) {}
}
