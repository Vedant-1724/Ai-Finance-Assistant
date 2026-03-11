package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.dto.BulkImportRequest;
import com.financeassistant.financeassistant.dto.ImportTransactionsResultDto;
import com.financeassistant.financeassistant.dto.TransactionDTO;
import com.financeassistant.financeassistant.entity.Company;
import com.financeassistant.financeassistant.entity.Transaction;
import com.financeassistant.financeassistant.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatementImportService {

    private static final Set<String> CSV_SOURCES = Set.of("CSV", "CSV_IMPORT");
    private static final Set<String> PDF_SOURCES = Set.of("PDF", "PDF_STATEMENT");
    private static final Set<String> IMAGE_SOURCES = Set.of(
            "IMAGE", "IMAGE_STATEMENT", "PNG", "JPG", "JPEG", "WEBP", "BMP", "TIFF", "HEIC", "HEIF"
    );
    private static final Set<String> UPI_SOURCES = Set.of(
            "UPI", "UPI_SCREENSHOT", "PHONEPE", "GOOGLE_PAY", "G_PAY", "GPAY", "PAYTM"
    );
    private static final Set<String> BANK_SYNC_SOURCES = Set.of(
            "BANK_SYNC", "SETU", "SETU_AA", "SETU_AA_MOCK", "SETU_AA_(MOCK)"
    );
    private static final Set<String> LLM_SOURCES = Set.of("LLM", "LLM_FALLBACK", "AI_FALLBACK", "AI_ASSISTED");
    private static final BigDecimal MAX_ALLOWED_AMOUNT = BigDecimal.valueOf(10_000_000L);

    private final TransactionRepository repo;
    private final TransactionEventPublisher eventPublisher;
    private final ReportingService reportingService;

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public ImportTransactionsResultDto importTransactions(Long companyId, BulkImportRequest request) {
        List<TransactionDTO> saved = new ArrayList<>();
        List<Long> importedIds = new ArrayList<>();
        Set<String> batchKeys = new HashSet<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Company companyRef = em.getReference(Company.class, companyId);
        int duplicates = 0;
        int skipped = 0;
        int rowNumber = 0;

        for (BulkImportRequest.ImportItem item : request.getTransactions()) {
            rowNumber++;
            if (item == null) {
                skipped++;
                errors.add("Row " + rowNumber + " was empty and was skipped.");
                continue;
            }

            if (item.getAmount() == null) {
                skipped++;
                errors.add("Row " + rowNumber + " was skipped because the amount is missing.");
                continue;
            }

            BigDecimal amount = item.getAmount().setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() == 0) {
                skipped++;
                errors.add("Row " + rowNumber + " was skipped because amount cannot be zero.");
                continue;
            }

            if (amount.abs().compareTo(MAX_ALLOWED_AMOUNT) > 0) {
                skipped++;
                errors.add("Row " + rowNumber + " was skipped because the amount exceeds the maximum allowed value.");
                continue;
            }

            LocalDate date = item.getDate();
            if (date == null) {
                skipped++;
                errors.add("Row " + rowNumber + " was skipped because the date is missing.");
                continue;
            }

            String description = sanitizeDescription(item.getDescription());
            if (!StringUtils.hasText(description)) {
                skipped++;
                errors.add("Row " + rowNumber + " was skipped because the description is missing.");
                continue;
            }

            String originalSource = item.getSource();
            String safeSource = normalizeSource(originalSource);
            if (StringUtils.hasText(originalSource) && !safeSource.equals(originalSource.trim().toUpperCase(Locale.ROOT))) {
                warnings.add("Row " + rowNumber + " used legacy source '" + originalSource + "' and was imported as '" + safeSource + "'.");
            }

            String duplicateKey = buildKey(date, amount, description);
            if (!batchKeys.add(duplicateKey)
                    || repo.existsByCompany_IdAndDateAndAmountAndDescriptionIgnoreCase(companyId, date, amount, description)) {
                duplicates++;
                warnings.add("Row " + rowNumber + " was skipped as a duplicate transaction.");
                continue;
            }

            Transaction tx = new Transaction();
            tx.setCompany(companyRef);
            tx.setDate(date);
            tx.setAmount(amount);
            tx.setDescription(description);
            tx.setSource(safeSource);
            tx.setType(amount.signum() > 0 ? Transaction.TransactionType.INCOME : Transaction.TransactionType.EXPENSE);

            Transaction savedTx = repo.save(tx);
            importedIds.add(savedTx.getId());
            saved.add(toDTO(savedTx));
        }

        if (!importedIds.isEmpty()) {
            eventPublisher.publishTransactionEvent(companyId, importedIds);
            reportingService.evictPnLCache(companyId);
            log.info("Imported {} statement transactions for company={} (duplicates={}, skipped={})",
                    importedIds.size(), companyId, duplicates, skipped);
        }

        return new ImportTransactionsResultDto(
                saved.size(),
                duplicates,
                skipped,
                saved,
                dedupe(warnings),
                dedupe(errors),
                buildMessage(saved.size(), duplicates, skipped)
        );
    }

    private String sanitizeDescription(String description) {
        if (!StringUtils.hasText(description)) {
            return "Imported Transaction";
        }
        String trimmed = description.trim().replaceAll("\\s+", " ");
        return trimmed.substring(0, Math.min(trimmed.length(), 500));
    }

    private String normalizeSource(String source) {
        if (!StringUtils.hasText(source)) {
            return "IMPORT";
        }
        String value = source.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (CSV_SOURCES.contains(value)) {
            return "CSV_IMPORT";
        }
        if (PDF_SOURCES.contains(value)) {
            return "PDF";
        }
        if (IMAGE_SOURCES.contains(value)) {
            return "IMAGE";
        }
        if (UPI_SOURCES.contains(value) || value.contains("UPI")) {
            return "UPI_SCREENSHOT";
        }
        if (BANK_SYNC_SOURCES.contains(value) || value.contains("SETU")) {
            return "BANK_SYNC";
        }
        if (LLM_SOURCES.contains(value)) {
            return "LLM_FALLBACK";
        }
        if ("IMPORT".equals(value) || "TEXT".equals(value) || "UNKNOWN".equals(value)) {
            return "IMPORT";
        }
        return "IMPORT";
    }

    private String buildKey(LocalDate date, BigDecimal amount, String description) {
        return date + "|" + amount.toPlainString() + "|" + description.toLowerCase(Locale.ROOT);
    }

    private List<String> dedupe(List<String> messages) {
        return new ArrayList<>(new LinkedHashSet<>(messages));
    }

    private String buildMessage(int imported, int duplicates, int skipped) {
        List<String> parts = new ArrayList<>();
        if (imported > 0) {
            parts.add("Imported " + imported + " transaction" + (imported == 1 ? "" : "s"));
        }
        if (duplicates > 0) {
            parts.add(duplicates + " duplicate" + (duplicates == 1 ? " was" : "s were") + " skipped");
        }
        if (skipped > 0) {
            parts.add(skipped + " invalid row" + (skipped == 1 ? " was" : "s were") + " skipped");
        }
        if (parts.isEmpty()) {
            return "No transactions were imported.";
        }
        return String.join(". ", parts) + ".";
    }

    private TransactionDTO toDTO(Transaction transaction) {
        return new TransactionDTO(
                transaction.getId(),
                transaction.getDate().toString(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getCategory() != null ? transaction.getCategory().getName() : null
        );
    }
}
