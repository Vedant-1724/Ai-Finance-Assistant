package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.dto.CreateTransactionRequest;
import com.financeassistant.financeassistant.dto.TransactionDTO;
import com.financeassistant.financeassistant.entity.Company;
import com.financeassistant.financeassistant.entity.Transaction;
import com.financeassistant.financeassistant.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * FIXES vs old finance-backend version:
 * 1. findByCompany(Pageable) → getTransactions() — matches controller call.
 * 2. create() → createTransaction() — matches controller call.
 * 3. Added TransactionEventPublisher injection + publishNewTransaction() call.
 * 4. Added ReportingService injection + evictPnLCache() call after save.
 * 5. toDTO() now passes t.getDate().toString() (String) not t.getDate() (LocalDate)
 *    because TransactionDTO now has 'String date' field.
 */
@Service
public class TransactionService {

    @Autowired
    private TransactionRepository repo;

    @Autowired
    private TransactionEventPublisher eventPublisher;

    @Autowired
    private ReportingService reportingService;

    @PersistenceContext
    private EntityManager em;

    public List<TransactionDTO> getTransactions(Long companyId) {
        return repo.findByCompanyIdOrderByDateDesc(companyId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public TransactionDTO createTransaction(Long companyId, CreateTransactionRequest req) {
        Transaction tx = new Transaction();
        tx.setCompany(em.getReference(Company.class, companyId));
        tx.setDate(req.getDate());
        tx.setAmount(req.getAmount());
        tx.setDescription(req.getDescription());
        Transaction saved = repo.save(tx);

        // Publish event to RabbitMQ → Python picks it up for anomaly detection
        eventPublisher.publishNewTransaction(companyId, saved.getId());

        // Evict P&L cache so the dashboard shows fresh data immediately
        reportingService.evictPnLCache(companyId);

        return toDTO(saved);
    }

    private TransactionDTO toDTO(Transaction t) {
        return new TransactionDTO(
                t.getId(),
                t.getDate().toString(),   // LocalDate → "YYYY-MM-DD" String
                t.getAmount(),
                t.getDescription(),
                t.getCategory() != null ? t.getCategory().getName() : null
        );
    }
}