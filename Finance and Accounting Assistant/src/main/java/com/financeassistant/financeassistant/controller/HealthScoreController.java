package com.financeassistant.financeassistant.controller;
// PATH: HealthScoreController.java
import com.financeassistant.financeassistant.entity.FinancialHealthScore;
import com.financeassistant.financeassistant.service.FinancialHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
@RestController @RequestMapping("/api/v1/{companyId}/health") @RequiredArgsConstructor
public class HealthScoreController {
    private final FinancialHealthService healthService;
    @GetMapping("/score")
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<FinancialHealthScore> score(@PathVariable Long companyId,
            @RequestParam(defaultValue="") String month) {
        LocalDate m = month.isBlank() ? LocalDate.now() : LocalDate.parse(month + "-01");
        return ResponseEntity.ok(healthService.getOrComputeScore(companyId, m));
    }
    @GetMapping("/history")
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<List<FinancialHealthScore>> history(@PathVariable Long companyId) {
        return ResponseEntity.ok(healthService.getHistory(companyId));
    }
}
