package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.dto.CreateTransactionRequest;
import com.financeassistant.financeassistant.dto.TransactionDTO;
import com.financeassistant.financeassistant.entity.Category;
import com.financeassistant.financeassistant.entity.Company;
import com.financeassistant.financeassistant.entity.Transaction;
import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TransactionService {

    private static final String DUPLICATE_MESSAGE = "A transaction with the same date, amount, and description already exists.";

    @Autowired
    private TransactionRepository repo;
    @Autowired
    private TransactionEventPublisher eventPublisher;
    @Autowired
    private ReportingService reportingService;
    @Autowired
    private AuditService auditService;
    @PersistenceContext
    private EntityManager em;

    public List<TransactionDTO> getTransactions(Long companyId) {
        return repo.findByCompanyIdOrderByDateDesc(companyId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public TransactionDTO createTransaction(Long companyId, CreateTransactionRequest req,
            User currentUser, String ipAddress) {
        BigDecimal amount = req.getAmount();
        String description = sanitizeDescription(req.getDescription());
        ensureNotDuplicate(companyId, req.getDate(), amount, description, null);

        Transaction tx = new Transaction();
        tx.setCompany(em.getReference(Company.class, companyId));
        tx.setDate(req.getDate());
        tx.setAmount(amount);
        tx.setDescription(description);

        if (req.getCategoryId() != null) {
            tx.setCategory(em.getReference(Category.class, req.getCategoryId()));
        }

        tx.setType(amount.signum() >= 0
                ? Transaction.TransactionType.INCOME
                : Transaction.TransactionType.EXPENSE);
        tx.setSource("MANUAL");

        if (req.getRecurring() != null && req.getRecurring()) {
            tx.setRecurring(true);
            tx.setRecurrenceInterval(req.getRecurrenceInterval());
            tx.setRecurrenceEndDate(req.getRecurrenceEndDate());
        }

        Transaction saved = repo.save(tx);

        eventPublisher.publishNewTransaction(companyId, saved.getId());
        reportingService.evictPnLCache(companyId);

        auditService.log(currentUser != null ? currentUser.getId() : null, companyId,
                AuditService.CREATE_TRANSACTION, "Transaction", saved.getId(),
                null, "amount=" + amount + " desc=" + description,
                ipAddress);

        return toDTO(saved);
    }

    @Transactional
    public TransactionDTO createTransaction(Long companyId, CreateTransactionRequest req) {
        return createTransaction(companyId, req, null, null);
    }

    @Transactional
    public TransactionDTO updateTransaction(Long companyId, Long transactionId,
            CreateTransactionRequest req,
            User currentUser, String ipAddress) {
        Transaction tx = repo.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        if (!tx.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        BigDecimal amount = req.getAmount();
        String description = sanitizeDescription(req.getDescription());
        ensureNotDuplicate(companyId, req.getDate(), amount, description, transactionId);

        String oldValue = "amount=" + tx.getAmount() + " desc=" + tx.getDescription();

        tx.setDate(req.getDate());
        tx.setAmount(amount);
        tx.setDescription(description);

        if (req.getCategoryId() != null) {
            tx.setCategory(em.getReference(Category.class, req.getCategoryId()));
        }

        tx.setType(amount.signum() >= 0
                ? Transaction.TransactionType.INCOME
                : Transaction.TransactionType.EXPENSE);

        if (req.getRecurring() != null) {
            tx.setRecurring(req.getRecurring());
            tx.setRecurrenceInterval(req.getRecurrenceInterval());
            tx.setRecurrenceEndDate(req.getRecurrenceEndDate());
        }

        Transaction saved = repo.save(tx);
        reportingService.evictPnLCache(companyId);

        String newValue = "amount=" + amount + " desc=" + description;
        auditService.log(currentUser != null ? currentUser.getId() : null, companyId,
                AuditService.UPDATE_TRANSACTION, "Transaction", transactionId,
                oldValue, newValue, ipAddress);

        return toDTO(saved);
    }

    @Transactional
    public void deleteTransaction(Long companyId, Long transactionId,
            User currentUser, String ipAddress) {
        Transaction tx = repo.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        if (!tx.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        String oldValue = "amount=" + tx.getAmount() + " desc=" + tx.getDescription();
        repo.deleteById(transactionId);
        reportingService.evictPnLCache(companyId);

        auditService.log(currentUser != null ? currentUser.getId() : null, companyId,
                AuditService.DELETE_TRANSACTION, "Transaction", transactionId,
                oldValue, null, ipAddress);
    }

    @Transactional
    public void deleteTransaction(Long companyId, Long transactionId) {
        deleteTransaction(companyId, transactionId, null, null);
    }

    private void ensureNotDuplicate(Long companyId, java.time.LocalDate date, BigDecimal amount, String description, Long excludeId) {
        boolean exists = excludeId == null
                ? repo.existsByCompany_IdAndDateAndAmountAndDescriptionIgnoreCase(companyId, date, amount, description)
                : repo.existsByCompany_IdAndDateAndAmountAndDescriptionIgnoreCaseAndIdNot(companyId, date, amount, description, excludeId);
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, DUPLICATE_MESSAGE);
        }
    }

    private String sanitizeDescription(String description) {
        if (!StringUtils.hasText(description)) {
            return "Transaction";
        }
        String trimmed = description.trim().replaceAll("\\s+", " ");
        return trimmed.substring(0, Math.min(trimmed.length(), 500));
    }

    private TransactionDTO toDTO(Transaction t) {
        return new TransactionDTO(
                t.getId(), t.getDate().toString(), t.getAmount(), t.getDescription(),
                t.getCategory() != null ? t.getCategory().getName() : null);
    }
}
