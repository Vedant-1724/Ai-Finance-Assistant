package com.financeassistant.financeassistant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionDTO(
        Long id,
        LocalDate date,
        BigDecimal amount,
        String description,
        String categoryName
) {}
