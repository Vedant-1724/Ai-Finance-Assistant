package com.financeassistant.financeassistant.dto;

import java.util.List;

public record BankSyncResultDto(
        int imported,
        int duplicates,
        List<TransactionDTO> transactions,
        String message
) {
}
