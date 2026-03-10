package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.dto.BankSyncStatusDto;
import com.financeassistant.financeassistant.dto.TransactionDTO;
import com.financeassistant.financeassistant.entity.BankSyncConsent;
import com.financeassistant.financeassistant.entity.Company;
import com.financeassistant.financeassistant.entity.Transaction;
import com.financeassistant.financeassistant.repository.BankSyncConsentRepository;
import com.financeassistant.financeassistant.repository.CompanyRepository;
import com.financeassistant.financeassistant.repository.TransactionRepository;
import com.financeassistant.financeassistant.service.banksync.BankSyncProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SetuBankService {

    private final TransactionRepository transactionRepository;
    private final CompanyRepository companyRepository;
    private final BankSyncConsentRepository bankSyncConsentRepository;
    private final List<BankSyncProvider> providers;

    @Value("${bank.sync.provider:AUTO}")
    private String configuredProvider;

    @Transactional
    public BankSyncStatusDto createConsent(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found."));

        BankSyncProvider provider = selectProvider();
        Optional<BankSyncConsent> latestOpt = bankSyncConsentRepository.findTopByCompanyIdOrderByCreatedAtDesc(companyId);
        BankSyncConsent latest = latestOpt.orElse(null);

        if (latest != null && latest.isExpired() && latest.getStatus() != BankSyncConsent.Status.EXPIRED) {
            latest.setStatus(BankSyncConsent.Status.EXPIRED);
            latest.setLastError("Bank consent expired.");
            bankSyncConsentRepository.save(latest);
        }

        if (isReusableConsent(latest, provider)) {
            return toStatusDto(latest, latest.getStatus() == BankSyncConsent.Status.CONSENT_GRANTED
                    ? "Consent is already active. Click connect again to import fresh bank transactions."
                    : latest.getLastError() != null ? latest.getLastError() : "Consent is already available for this company.");
        }

        BankSyncConsent consent = BankSyncConsent.builder()
                .companyId(companyId)
                .providerKey(provider.getKey())
                .status(BankSyncConsent.Status.CONSENT_REQUIRED)
                .stateToken(UUID.randomUUID().toString())
                .mockFallback(provider.isMockFallback())
                .consentExpiresAt(LocalDateTime.now().plusHours(72))
                .build();

        BankSyncProvider.ConsentCreation creation = provider.createConsent(company, consent);
        consent.setProviderConsentId(creation.consentId());
        consent.setConsentUrl(creation.consentUrl());
        consent.setStatus(normalizeStatus(creation.status()));
        consent.setConsentExpiresAt(creation.expiresAt() != null ? creation.expiresAt() : consent.getConsentExpiresAt());
        consent.setLastError(null);

        BankSyncConsent saved = bankSyncConsentRepository.save(consent);
        return toStatusDto(saved, creation.message());
    }

    @Transactional(readOnly = true)
    public BankSyncStatusDto getSyncStatus(Long companyId) {
        Optional<BankSyncConsent> latest = bankSyncConsentRepository.findTopByCompanyIdOrderByCreatedAtDesc(companyId);
        if (latest.isPresent()) {
            return toStatusDto(latest.get(), latest.get().getLastError());
        }

        BankSyncProvider provider = selectProvider();
        return new BankSyncStatusDto(
                provider.getKey(),
                "NOT_STARTED",
                null,
                null,
                provider.isMockFallback(),
                provider.isMockFallback()
                        ? "Bank sync is ready in demo mode until live Setu credentials are configured."
                        : "No bank consent has been started yet.",
                null,
                null
        );
    }

    @Transactional
    public BankSyncStatusDto handleCallback(String stateToken, String consentId, String providerStatus, String error) {
        BankSyncConsent consent = bankSyncConsentRepository.findByStateToken(stateToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid bank consent state."));

        if (StringUtils.hasText(consentId)) {
            consent.setProviderConsentId(consentId);
        }

        if (StringUtils.hasText(error)) {
            consent.setStatus(BankSyncConsent.Status.FAILED);
            consent.setLastError(error);
        } else {
            consent.setStatus(normalizeStatus(providerStatus));
            consent.setLastError(null);
        }

        if (consent.isExpired()) {
            consent.setStatus(BankSyncConsent.Status.EXPIRED);
            consent.setLastError("Bank consent expired before the callback was completed.");
        }

        BankSyncConsent saved = bankSyncConsentRepository.save(consent);
        String message = saved.getStatus() == BankSyncConsent.Status.CONSENT_GRANTED
                ? "Bank consent recorded. Return to FinanceAI and click connect again to sync transactions."
                : saved.getLastError();
        return toStatusDto(saved, message);
    }

    @Transactional
    public List<TransactionDTO> syncTransactions(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found."));

        BankSyncConsent consent = bankSyncConsentRepository.findTopByCompanyIdOrderByCreatedAtDesc(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Start the bank consent flow before syncing transactions."));

        if (consent.isExpired()) {
            consent.setStatus(BankSyncConsent.Status.EXPIRED);
            consent.setLastError("Bank consent expired.");
            bankSyncConsentRepository.save(consent);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Your bank consent has expired. Start a new consent flow and try again.");
        }

        if (consent.getStatus() == BankSyncConsent.Status.CONSENT_REQUIRED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bank consent is still pending. Complete the provider approval and then try syncing again.");
        }

        if (consent.getStatus() == BankSyncConsent.Status.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    consent.getLastError() != null ? consent.getLastError() : "Bank consent failed. Start a new consent flow.");
        }

        BankSyncProvider provider = providerByKey(consent.getProviderKey())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "The configured bank-sync provider is not available."));

        try {
            BankSyncProvider.SyncPayload payload = provider.syncTransactions(company, consent);
            List<Transaction> transactionsToSave = payload.transactions().stream()
                    .filter(txn -> !transactionRepository.existsByCompany_IdAndDateAndAmountAndDescriptionAndSource(
                            companyId,
                            txn.date(),
                            txn.amount(),
                            txn.description(),
                            txn.source()))
                    .map(txn -> toEntity(company, txn))
                    .toList();

            List<Transaction> saved = transactionRepository.saveAll(transactionsToSave);
            consent.setStatus(BankSyncConsent.Status.SYNCED);
            consent.setLastSyncedAt(LocalDateTime.now());
            consent.setLastError(null);
            bankSyncConsentRepository.save(consent);

            log.info("Bank sync completed for company={} provider={} imported={}", companyId, provider.getKey(), saved.size());
            return saved.stream().map(this::toDto).toList();
        } catch (IllegalStateException ex) {
            consent.setStatus(BankSyncConsent.Status.FAILED);
            consent.setLastError(ex.getMessage());
            bankSyncConsentRepository.save(consent);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        }
    }

    private boolean isReusableConsent(BankSyncConsent consent, BankSyncProvider provider) {
        return consent != null
                && provider.getKey().equalsIgnoreCase(consent.getProviderKey())
                && !consent.isExpired()
                && (consent.getStatus() == BankSyncConsent.Status.CONSENT_REQUIRED
                || consent.getStatus() == BankSyncConsent.Status.CONSENT_GRANTED
                || consent.getStatus() == BankSyncConsent.Status.SYNCED);
    }

    private BankSyncProvider selectProvider() {
        String requested = configuredProvider == null ? "AUTO" : configuredProvider.trim().toUpperCase(Locale.ROOT);

        if ("MOCK".equals(requested)) {
            return providerByKey("MOCK")
                    .orElseThrow(() -> new IllegalStateException("Mock bank-sync provider is unavailable."));
        }

        if ("SETU".equals(requested)) {
            BankSyncProvider setu = providerByKey("SETU")
                    .orElseThrow(() -> new IllegalStateException("Setu bank-sync provider is unavailable."));
            if (!setu.isConfigured()) {
                throw new IllegalStateException("Setu bank-sync provider was explicitly selected but is not configured.");
            }
            return setu;
        }

        Optional<BankSyncProvider> setu = providerByKey("SETU").filter(BankSyncProvider::isConfigured);
        return setu.orElseGet(() -> providerByKey("MOCK")
                .orElseThrow(() -> new IllegalStateException("No bank-sync provider is available.")));
    }

    private Optional<BankSyncProvider> providerByKey(String key) {
        return providers.stream()
                .filter(provider -> provider.getKey().equalsIgnoreCase(key))
                .findFirst();
    }

    private BankSyncConsent.Status normalizeStatus(String rawStatus) {
        String value = rawStatus == null ? "" : rawStatus.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "CONSENT_GRANTED", "GRANTED", "APPROVED", "ACTIVE", "SYNCED" -> BankSyncConsent.Status.CONSENT_GRANTED;
            case "FAILED", "ERROR", "REJECTED" -> BankSyncConsent.Status.FAILED;
            case "EXPIRED" -> BankSyncConsent.Status.EXPIRED;
            default -> BankSyncConsent.Status.CONSENT_REQUIRED;
        };
    }

    private Transaction toEntity(Company company, BankSyncProvider.FetchedTransaction txn) {
        Transaction transaction = new Transaction();
        transaction.setCompany(company);
        transaction.setDate(txn.date());
        transaction.setAmount(txn.amount());
        transaction.setDescription(txn.description());
        transaction.setReferenceNumber(txn.referenceNumber());
        transaction.setAccount(txn.account());
        transaction.setSource(txn.source());
        transaction.setType(txn.amount().signum() < 0
                ? Transaction.TransactionType.EXPENSE
                : Transaction.TransactionType.INCOME);
        return transaction;
    }

    private TransactionDTO toDto(Transaction transaction) {
        return new TransactionDTO(
                transaction.getId(),
                transaction.getDate().toString(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getCategory() != null ? transaction.getCategory().getName() : null
        );
    }

    private BankSyncStatusDto toStatusDto(BankSyncConsent consent, String message) {
        return new BankSyncStatusDto(
                consent.getProviderKey(),
                consent.getStatus().name(),
                consent.getConsentUrl(),
                consent.getProviderConsentId(),
                consent.isMockFallback(),
                message,
                consent.getConsentExpiresAt() != null ? consent.getConsentExpiresAt().toString() : null,
                consent.getLastSyncedAt() != null ? consent.getLastSyncedAt().toString() : null
        );
    }
}
