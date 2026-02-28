package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.dto.CreateTransactionRequest;
import com.financeassistant.financeassistant.dto.TransactionDTO;
import com.financeassistant.financeassistant.entity.Company;
import com.financeassistant.financeassistant.entity.Transaction;
import com.financeassistant.financeassistant.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

        eventPublisher.publishNewTransaction(companyId, saved.getId());
        reportingService.evictPnLCache(companyId);

        return toDTO(saved);
    }

    @Transactional
    public void deleteTransaction(Long companyId, Long transactionId) {
        Transaction tx = repo.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Transaction not found"));

        // Security: ensure transaction belongs to this company
        if (!tx.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Access denied");
        }

        repo.deleteById(transactionId);
        reportingService.evictPnLCache(companyId);
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