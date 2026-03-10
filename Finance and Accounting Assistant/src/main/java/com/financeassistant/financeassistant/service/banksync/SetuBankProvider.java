package com.financeassistant.financeassistant.service.banksync;

import com.financeassistant.financeassistant.entity.BankSyncConsent;
import com.financeassistant.financeassistant.entity.Company;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class SetuBankProvider implements BankSyncProvider {

    private final RestTemplate restTemplate;

    @Value("${setu.base-url:}")
    private String baseUrl;

    @Value("${setu.client-id:}")
    private String clientId;

    @Value("${setu.client-secret:}")
    private String clientSecret;

    @Value("${setu.consent-path:/consents}")
    private String consentPath;

    @Value("${setu.transactions-path:/consents/{consentId}/transactions}")
    private String transactionsPath;

    @Value("${setu.callback-url:http://localhost:8080/api/v1/setu/callback}")
    private String callbackUrl;

    public SetuBankProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getKey() {
        return "SETU";
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(baseUrl) && StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
    }

    @Override
    public boolean isMockFallback() {
        return false;
    }

    @Override
    public ConsentCreation createConsent(Company company, BankSyncConsent consent) {
        if (!isConfigured()) {
            throw new IllegalStateException("Setu bank sync credentials are not configured.");
        }

        LocalDateTime expiresAt = consent.getConsentExpiresAt() != null
                ? consent.getConsentExpiresAt()
                : LocalDateTime.now().plusHours(72);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("companyId", company.getId());
        payload.put("companyName", company.getName());
        payload.put("state", consent.getStateToken());
        payload.put("redirectUrl", callbackUrl);
        payload.put("validTill", expiresAt.toString());

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    buildUrl(consentPath),
                    new HttpEntity<>(payload, buildHeaders()),
                    Map.class
            );
            Map<String, Object> body = response.getBody() != null ? response.getBody() : Map.of();

            String consentUrl = extractString(body, "consentUrl", "url", "consent_link");
            String consentId = extractString(body, "consentId", "id", "consent_id");
            String status = normalizeStatus(extractString(body, "status", "consentStatus"));
            LocalDateTime providerExpiresAt = parseDateTime(extractString(body, "expiresAt", "expiry", "validTill"), expiresAt);

            return new ConsentCreation(
                    consentUrl,
                    consentId,
                    status,
                    providerExpiresAt,
                    "Setu consent created. Complete the approval in the provider window, then return and sync again."
            );
        } catch (RestClientException ex) {
            throw new IllegalStateException("Failed to create Setu consent: " + ex.getMessage(), ex);
        }
    }

    @Override
    public SyncPayload syncTransactions(Company company, BankSyncConsent consent) {
        if (!isConfigured()) {
            throw new IllegalStateException("Setu bank sync credentials are not configured.");
        }
        if (!StringUtils.hasText(consent.getProviderConsentId())) {
            throw new IllegalStateException("Setu consent is not ready for syncing yet.");
        }

        String path = transactionsPath.replace("{consentId}", consent.getProviderConsentId());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    buildUrl(path),
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    Map.class
            );
            Map<String, Object> body = response.getBody() != null ? response.getBody() : Map.of();
            List<Map<String, Object>> rawTransactions = extractTransactions(body);
            List<FetchedTransaction> fetched = new ArrayList<>();

            for (Map<String, Object> raw : rawTransactions) {
                BigDecimal amount = parseAmount(raw.get("amount"));
                if (amount == null) {
                    amount = parseAmount(extractObject(raw, "value", "transactionAmount"));
                }
                if (amount == null) {
                    continue;
                }

                String direction = uppercase(extractString(raw, "direction", "type", "transactionType", "entryType"));
                if (direction.contains("DEBIT") || direction.contains("WITHDRAWAL") || direction.contains("EXPENSE")) {
                    amount = amount.abs().negate();
                } else if (direction.contains("CREDIT") || direction.contains("DEPOSIT") || direction.contains("INCOME")) {
                    amount = amount.abs();
                }

                LocalDate date = parseDate(extractString(raw, "date", "transactionDate", "valueDate", "postedAt", "posted_on"));
                if (date == null) {
                    date = LocalDate.now();
                }

                String description = firstNonBlank(
                        extractString(raw, "description", "narration", "remarks", "merchant", "summary"),
                        "Bank synced transaction"
                );
                String referenceNumber = extractString(raw, "referenceNumber", "reference", "txnId", "utr", "transactionId");
                String account = extractString(raw, "account", "accountId", "accountNumber", "maskedAccountNumber");

                fetched.add(new FetchedTransaction(
                        date,
                        amount,
                        description,
                        referenceNumber,
                        account,
                        "Setu AA"
                ));
            }

            return new SyncPayload(fetched, "Imported transactions from Setu.");
        } catch (RestClientException ex) {
            throw new IllegalStateException("Failed to fetch Setu transactions: " + ex.getMessage(), ex);
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-client-id", clientId);
        headers.set("x-client-secret", clientSecret);
        return headers;
    }

    private String buildUrl(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return StringUtils.trimTrailingCharacter(baseUrl, '/') + (path.startsWith("/") ? path : "/" + path);
    }

    private String extractString(Map<String, Object> source, String... keys) {
        Object value = extractObject(source, keys);
        return value != null ? String.valueOf(value) : null;
    }

    private Object extractObject(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object direct = source.get(key);
            if (direct != null) {
                return direct;
            }
        }
        Object data = source.get("data");
        if (data instanceof Map<?, ?> nested) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nestedMap = (Map<String, Object>) nested;
            for (String key : keys) {
                Object direct = nestedMap.get(key);
                if (direct != null) {
                    return direct;
                }
            }
        }
        return null;
    }

    private List<Map<String, Object>> extractTransactions(Map<String, Object> body) {
        Object transactions = body.get("transactions");
        if (transactions instanceof List<?> list) {
            return castTransactionList(list);
        }

        Object data = body.get("data");
        if (data instanceof Map<?, ?> nestedMap) {
            Object nestedTransactions = nestedMap.get("transactions");
            if (nestedTransactions instanceof List<?> list) {
                return castTransactionList(list);
            }
        }

        if (data instanceof List<?> list) {
            return castTransactionList(list);
        }

        return List.of();
    }

    private List<Map<String, Object>> castTransactionList(List<?> list) {
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> rawMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) rawMap;
                mapped.add(cast);
            }
        }
        return mapped;
    }

    private BigDecimal parseAmount(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String raw = String.valueOf(value).replace(",", "").trim();
        if (raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(value).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private LocalDateTime parseDateTime(String value, LocalDateTime fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }
        return fallback;
    }

    private String normalizeStatus(String status) {
        String value = uppercase(status);
        if (value.contains("GRANTED") || value.contains("APPROVED") || value.contains("ACTIVE") || value.contains("SUCCESS")) {
            return "CONSENT_GRANTED";
        }
        if (value.contains("FAILED") || value.contains("ERROR") || value.contains("REJECT")) {
            return "FAILED";
        }
        if (value.contains("EXPIRED")) {
            return "EXPIRED";
        }
        return "CONSENT_REQUIRED";
    }

    private String firstNonBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String uppercase(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }
}
