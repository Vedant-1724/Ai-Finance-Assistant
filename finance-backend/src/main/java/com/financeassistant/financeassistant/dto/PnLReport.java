package com.financeassistant.financeassistant.dto;

import java.math.BigDecimal;

public class PnLReport {

    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netProfit;

    public PnLReport(BigDecimal totalIncome, BigDecimal totalExpense) {
        this.totalIncome = totalIncome != null ? totalIncome : BigDecimal.ZERO;
        this.totalExpense = totalExpense != null ? totalExpense : BigDecimal.ZERO;
        this.netProfit = this.totalIncome.subtract(this.totalExpense);
    }

    public BigDecimal getTotalIncome() { return totalIncome; }
    public BigDecimal getTotalExpense() { return totalExpense; }
    public BigDecimal getNetProfit() { return netProfit; }
}