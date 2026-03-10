package com.financeassistant.financeassistant.service.banksync;

import com.financeassistant.financeassistant.entity.BankSyncConsent;
import com.financeassistant.financeassistant.entity.Company;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface BankSyncProvider {

    String getKey();

    boolean isConfigured();

    boolean isMockFallback();

    ConsentCreation createConsent(Company company, BankSyncConsent consent);

    SyncPayload syncTransactions(Company company, BankSyncConsent consent);

    record ConsentCreation(
            String consentUrl,
            String consentId,
            String status,
            LocalDateTime expiresAt,
            String message
    ) {
    }

    record SyncPayload(
            List<FetchedTransaction> transactions,
            String message
    ) {
    }

    record FetchedTransaction(
            LocalDate date,
            BigDecimal amount,
            String description,
            String referenceNumber,
            String account,
            String source
    ) {
    }
}
