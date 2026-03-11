package com.financeassistant.financeassistant.dto;

import java.util.List;

public record ImportTransactionsResultDto(
        int imported,
        int duplicates,
        int skipped,
        List<TransactionDTO> transactions,
        List<String> warnings,
        List<String> errors,
        String message
) {
}
