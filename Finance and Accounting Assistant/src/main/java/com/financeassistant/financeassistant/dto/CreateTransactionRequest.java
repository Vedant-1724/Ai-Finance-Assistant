package com.financeassistant.financeassistant.dto;

// PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/financeassistant/dto/CreateTransactionRequest.java
// UPDATED: Added recurring fields

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateTransactionRequest {

    @NotNull
    private LocalDate date;

    @NotNull
    private BigDecimal amount;

    @NotNull
    private String description;

    private Long categoryId;

    // NEW — recurring transaction fields (all optional)
    private Boolean recurring;
    private String recurrenceInterval;   // DAILY / WEEKLY / MONTHLY / YEARLY
    private LocalDate recurrenceEndDate;
}
