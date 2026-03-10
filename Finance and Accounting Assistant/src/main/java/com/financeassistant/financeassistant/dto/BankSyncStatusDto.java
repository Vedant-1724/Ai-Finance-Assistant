package com.financeassistant.financeassistant.dto;

public record BankSyncStatusDto(
        String provider,
        String status,
        String consentUrl,
        String consentId,
        boolean mockFallback,
        String message,
        String expiresAt,
        String lastSyncedAt
) {
}
