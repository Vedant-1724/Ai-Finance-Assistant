package com.financeassistant.financeassistant.dto;

import java.math.BigDecimal;

/**
 * FIX: Changed 'LocalDate date' → 'String date'
 * because TransactionService.toDTO() calls t.getDate().toString()
 * which returns a "YYYY-MM-DD" String, not a LocalDate.
 * The old record (LocalDate date) would cause a type mismatch at compile time.
 */
public record TransactionDTO(
        Long id,
        String date,           // ← was LocalDate — now String "YYYY-MM-DD"
        BigDecimal amount,
        String description,
        String categoryName
) {}