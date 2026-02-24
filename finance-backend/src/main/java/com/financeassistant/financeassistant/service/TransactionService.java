package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.dto.CreateTransactionRequest;
import com.financeassistant.financeassistant.dto.TransactionDTO;
import com.financeassistant.financeassistant.entity.Transaction;
import com.financeassistant.financeassistant.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    @Autowired
    private TransactionRepository repo;

    @PersistenceContext
    private EntityManager em;

    public List<TransactionDTO> findByCompany(Long companyId, Pageable pageable) {
        return repo.findByCompanyId(companyId, pageable)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public TransactionDTO create(Long companyId, CreateTransactionRequest req) {
        Transaction tx = new Transaction();
        tx.setCompany(em.getReference(
                com.financeassistant.financeassistant.entity.Company.class, companyId));
        tx.setDate(req.getDate());
        tx.setAmount(req.getAmount());
        tx.setDescription(req.getDescription());
        Transaction saved = repo.save(tx);
        return toDTO(saved);
    }

    private TransactionDTO toDTO(Transaction t) {
        return new TransactionDTO(
                t.getId(),
                t.getDate(),
                t.getAmount(),
                t.getDescription(),
                t.getCategory() != null ? t.getCategory().getName() : null
        );
    }
}