package com.financeassistant.financeassistant.controller;

// PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/financeassistant/controller/BudgetController.java

import com.financeassistant.financeassistant.service.BudgetService;
import com.financeassistant.financeassistant.service.BudgetService.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/{companyId}/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    /** GET /api/v1/{companyId}/budgets/variance?year=2026&month=3 */
    @GetMapping("/variance")
    @PreAuthorize("@companySecurityService.canEditFinance(#companyId, authentication)")
    public ResponseEntity<List<BudgetVarianceDTO>> getVariance(
            @PathVariable Long companyId,
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "0") int month) {
        int y = year  == 0 ? LocalDate.now().getYear()       : year;
        int m = month == 0 ? LocalDate.now().getMonthValue() : month;
        return ResponseEntity.ok(budgetService.getVariance(companyId, y, m));
    }

    /** POST /api/v1/{companyId}/budgets — create or update a budget entry */
    @PostMapping
    @PreAuthorize("@companySecurityService.canEditFinance(#companyId, authentication)")
    public ResponseEntity<BudgetDTO> upsert(
            @PathVariable Long companyId,
            @RequestBody UpsertBudgetRequest req) {
        return ResponseEntity.ok(budgetService.upsertBudget(companyId, req));
    }
}
