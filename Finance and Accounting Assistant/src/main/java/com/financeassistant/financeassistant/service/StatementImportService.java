package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.dto.BulkImportRequest;
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

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatementImportService {

    private final TransactionRepository       repo;
    private final TransactionEventPublisher   eventPublisher;
    private final ReportingService            reportingService;

    @PersistenceContext
    private EntityManager em;

    /**
     * Bulk-import transactions parsed from a bank statement.
     *
     * SECURITY:
     * - Amount is validated: max ₹1 crore per transaction to prevent manipulation.
     * - Description is trimmed and capped at 512 chars.
     * - Source field is sanitized.
     * - Company ownership is enforced by @PreAuthorize at the controller layer.
     *
     * @param companyId  Authenticated company (ownership already verified by controller)
     * @param request    Parsed, pre-sanitized transactions from Python parser
     * @return           List of saved TransactionDTOs
     */
    @Transactional
    public List<TransactionDTO> importTransactions(Long companyId, BulkImportRequest request) {
        List<TransactionDTO> saved   = new ArrayList<>();
        List<Long>           txnIds  = new ArrayList<>();

        for (BulkImportRequest.ImportItem item : request.getTransactions()) {
            // Server-side amount validation (defense-in-depth)
            if (item.getAmount() == null) continue;
            double abs = Math.abs(item.getAmount().doubleValue());
            if (abs == 0 || abs > 10_000_000) {  // skip zero or > ₹1 crore
                log.warn("Skipping suspicious import amount {} for company={}", item.getAmount(), companyId);
                continue;
            }

            Transaction tx = new Transaction();
            tx.setCompany(em.getReference(Company.class, companyId));
            tx.setDate(item.getDate());
            tx.setAmount(item.getAmount());

            // Sanitize description: trim whitespace, cap length
            String desc = item.getDescription() == null ? "Imported Transaction"
                    : item.getDescription().trim().substring(0, Math.min(item.getDescription().trim().length(), 512));
            tx.setDescription(desc);

            // Source: only allow known safe values
            String rawSource = item.getSource() == null ? "IMPORT" : item.getSource().trim().toUpperCase();
            String safeSource = rawSource.matches("CSV_IMPORT|PDF|UPI_SCREENSHOT|IMAGE|TEXT|IMPORT")
                    ? rawSource : "IMPORT";
            tx.setSource(safeSource);

            Transaction savedTx = repo.save(tx);
            txnIds.add(savedTx.getId());
            saved.add(toDTO(savedTx));
        }

        if (!txnIds.isEmpty()) {
            // Publish bulk event for anomaly detection
            eventPublisher.publishTransactionEvent(companyId, txnIds);
            // Evict P&L cache so dashboard reflects new data
            reportingService.evictPnLCache(companyId);
            log.info("Imported {} transactions for company={}", txnIds.size(), companyId);
        }

        return saved;
    }

    private TransactionDTO toDTO(Transaction t) {
        return new TransactionDTO(
                t.getId(),
                t.getDate().toString(),
                t.getAmount(),
                t.getDescription(),
                t.getCategory() != null ? t.getCategory().getName() : null
        );
    }
}