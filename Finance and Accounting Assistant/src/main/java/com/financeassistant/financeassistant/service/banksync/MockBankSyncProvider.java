package com.financeassistant.financeassistant.service.banksync;

import com.financeassistant.financeassistant.entity.BankSyncConsent;
import com.financeassistant.financeassistant.entity.Company;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class MockBankSyncProvider implements BankSyncProvider {

    @Override
    public String getKey() {
        return "MOCK";
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public boolean isMockFallback() {
        return true;
    }

    @Override
    public ConsentCreation createConsent(Company company, BankSyncConsent consent) {
        String consentId = consent.getProviderConsentId();
        if (consentId == null || consentId.isBlank()) {
            consentId = "mock-" + UUID.randomUUID();
        }

        LocalDateTime expiresAt = consent.getConsentExpiresAt() != null
                ? consent.getConsentExpiresAt()
                : LocalDateTime.now().plusHours(72);

        return new ConsentCreation(
                "https://mock-aa.setu.co/consent?id=" + consentId,
                consentId,
                "CONSENT_GRANTED",
                expiresAt,
                "Live Setu credentials are not configured, so FinanceAI is using the safe demo bank-sync provider."
        );
    }

    @Override
    public SyncPayload syncTransactions(Company company, BankSyncConsent consent) {
        LocalDate today = LocalDate.now();
        List<FetchedTransaction> transactions = List.of(
                new FetchedTransaction(today.minusDays(1), new BigDecimal("-850.00"), "Zomato Meals", "MOCK-001", "Business Current Account", "Setu AA (Mock)"),
                new FetchedTransaction(today.minusDays(2), new BigDecimal("-4500.00"), "AWS Cloud Services", "MOCK-002", "Business Current Account", "Setu AA (Mock)"),
                new FetchedTransaction(today.minusDays(3), new BigDecimal("25000.00"), "Client Payment ZXC", "MOCK-003", "Business Current Account", "Setu AA (Mock)"),
                new FetchedTransaction(today.minusDays(5), new BigDecimal("-1200.00"), "Uber Rides", "MOCK-004", "Business Current Account", "Setu AA (Mock)"),
                new FetchedTransaction(today.minusDays(6), new BigDecimal("-3200.00"), "Office Supplies", "MOCK-005", "Business Current Account", "Setu AA (Mock)")
        );

        return new SyncPayload(transactions, "Imported transactions from the demo bank-sync provider.");
    }
}
