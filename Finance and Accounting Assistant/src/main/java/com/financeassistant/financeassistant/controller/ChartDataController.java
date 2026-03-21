package com.financeassistant.financeassistant.controller;
// PATH: ChartDataController.java
import com.financeassistant.financeassistant.service.ChartDataService;
import com.financeassistant.financeassistant.service.ChartDataService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/{companyId}/charts") @RequiredArgsConstructor
public class ChartDataController {
    private final ChartDataService chartService;
    @GetMapping
    @PreAuthorize("@companySecurityService.isCompanyMember(#companyId, authentication)")
    public ResponseEntity<ChartDataResponse> getCharts(
            @PathVariable Long companyId,
            @RequestParam(defaultValue="6") int months) {
        return ResponseEntity.ok(chartService.getChartData(companyId, Math.min(months, 24)));
    }
}
