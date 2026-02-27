package com.financeassistant.financeassistant.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * FIX: Added @Builder pattern and CategoryBreakdown inner class.
 * The old PnLReport was a plain class with only totalIncome/totalExpense.
 * ReportingService uses PnLReport.builder() and CategoryBreakdown — this file
 * must match exactly or you will get compile errors.
 */
public class PnLReport {

    private String period;
    private String startDate;
    private String endDate;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netProfit;
    private List<CategoryBreakdown> breakdown;

    // ── No-arg constructor (needed for Jackson deserialization) ───────────────
    public PnLReport() {}

    // ── All-args constructor ──────────────────────────────────────────────────
    public PnLReport(String period, String startDate, String endDate,
                     BigDecimal totalIncome, BigDecimal totalExpense,
                     BigDecimal netProfit, List<CategoryBreakdown> breakdown) {
        this.period       = period;
        this.startDate    = startDate;
        this.endDate      = endDate;
        this.totalIncome  = totalIncome;
        this.totalExpense = totalExpense;
        this.netProfit    = netProfit;
        this.breakdown    = breakdown;
    }

    // ── Builder ───────────────────────────────────────────────────────────────
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String period;
        private String startDate;
        private String endDate;
        private BigDecimal totalIncome;
        private BigDecimal totalExpense;
        private BigDecimal netProfit;
        private List<CategoryBreakdown> breakdown;

        public Builder period(String period)               { this.period = period; return this; }
        public Builder startDate(String startDate)         { this.startDate = startDate; return this; }
        public Builder endDate(String endDate)             { this.endDate = endDate; return this; }
        public Builder totalIncome(BigDecimal v)           { this.totalIncome = v; return this; }
        public Builder totalExpense(BigDecimal v)          { this.totalExpense = v; return this; }
        public Builder netProfit(BigDecimal v)             { this.netProfit = v; return this; }
        public Builder breakdown(List<CategoryBreakdown> b){ this.breakdown = b; return this; }

        public PnLReport build() {
            return new PnLReport(period, startDate, endDate,
                    totalIncome, totalExpense, netProfit, breakdown);
        }
    }

    // ── Inner class ───────────────────────────────────────────────────────────
    public static class CategoryBreakdown {
        private String categoryName;
        private BigDecimal amount;

        public CategoryBreakdown() {}
        public CategoryBreakdown(String categoryName, BigDecimal amount) {
            this.categoryName = categoryName;
            this.amount = amount;
        }

        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────
    public String getPeriod()                          { return period; }
    public void setPeriod(String period)               { this.period = period; }
    public String getStartDate()                       { return startDate; }
    public void setStartDate(String startDate)         { this.startDate = startDate; }
    public String getEndDate()                         { return endDate; }
    public void setEndDate(String endDate)             { this.endDate = endDate; }
    public BigDecimal getTotalIncome()                 { return totalIncome; }
    public void setTotalIncome(BigDecimal totalIncome) { this.totalIncome = totalIncome; }
    public BigDecimal getTotalExpense()                { return totalExpense; }
    public void setTotalExpense(BigDecimal v)          { this.totalExpense = v; }
    public BigDecimal getNetProfit()                   { return netProfit; }
    public void setNetProfit(BigDecimal netProfit)     { this.netProfit = netProfit; }
    public List<CategoryBreakdown> getBreakdown()      { return breakdown; }
    public void setBreakdown(List<CategoryBreakdown> b){ this.breakdown = b; }
}