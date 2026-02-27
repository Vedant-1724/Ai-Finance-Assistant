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

    // ── FIX 1: Renamed from findByCompany → getTransactions (matches TransactionController)
    //           Removed Pageable — TransactionRepository has no Pageable overload
    public List<TransactionDTO> getTransactions(Long companyId) {
        return repo.findByCompanyIdOrderByDateDesc(companyId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ── FIX 2: Renamed from create → createTransaction (matches TransactionController)
    @Transactional
    public TransactionDTO createTransaction(Long companyId, CreateTransactionRequest req) {
        Transaction tx = new Transaction();
        tx.setCompany(em.getReference(Company.class, companyId));
        tx.setDate(req.getDate());
        tx.setAmount(req.getAmount());
        tx.setDescription(req.getDescription());
        Transaction saved = repo.save(tx);

        // ── FIX 3: publishNewTransaction (singular) — matches TransactionEventPublisher exactly
        eventPublisher.publishNewTransaction(companyId, saved.getId());

        // ── Evict P&L cache so Dashboard shows fresh data after every new transaction
        reportingService.evictPnLCache(companyId);

        return toDTO(saved);
    }

    // ── Maps Transaction entity → TransactionDTO for the REST response
    private TransactionDTO toDTO(Transaction t) {
        return new TransactionDTO(
                t.getId(),
                t.getDate().toString(),         // LocalDate → "YYYY-MM-DD" String
                t.getAmount(),
                t.getDescription(),
                t.getCategory() != null ? t.getCategory().getName() : null
        );
    }
}