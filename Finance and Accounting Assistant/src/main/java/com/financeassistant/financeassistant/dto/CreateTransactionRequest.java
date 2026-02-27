package com.financeassistant.financeassistant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for POST /api/v1/{companyId}/transactions
 * Example JSON: { "date": "2026-02-25", "amount": 50000.00, "description": "Client Payment" }
 */
public class CreateTransactionRequest {

    private LocalDate date;
    private BigDecimal amount;
    private String description;

    public CreateTransactionRequest() {}

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}