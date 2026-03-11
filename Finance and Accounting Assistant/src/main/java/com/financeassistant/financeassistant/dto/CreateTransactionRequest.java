package com.financeassistant.financeassistant.dto;

// PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/financeassistant/dto/CreateTransactionRequest.java
// UPDATED: Added recurring fields

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateTransactionRequest {

    @NotNull
    private LocalDate date;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "-10000000.00", message = "Amount must not be less than -₹1 crore")
    @DecimalMax(value = "10000000.00", message = "Amount must not exceed ₹1 crore")
    private BigDecimal amount;

    @NotNull(message = "Description is required")
    @NotBlank(message = "Description cannot be blank")
    @Size(max = 500, message = "Description is too long")
    private String description;

    private Long categoryId;

    // NEW — recurring transaction fields (all optional)
    private Boolean recurring;
    private String recurrenceInterval; // DAILY / WEEKLY / MONTHLY / YEARLY
    private LocalDate recurrenceEndDate;
}

