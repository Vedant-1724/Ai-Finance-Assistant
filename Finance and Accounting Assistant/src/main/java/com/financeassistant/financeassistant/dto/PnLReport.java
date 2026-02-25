package com.financeassistant.financeassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PnLReport {

    private String period;         // e.g. "month", "quarter", "year"
    private String startDate;      // e.g. "2026-02-01"
    private String endDate;        // e.g. "2026-02-28"
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netProfit;
    private List<CategoryBreakdown> breakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryBreakdown {
        private String categoryName;
        private BigDecimal amount;
        private String type;       // "INCOME" or "EXPENSE"
    }
}
