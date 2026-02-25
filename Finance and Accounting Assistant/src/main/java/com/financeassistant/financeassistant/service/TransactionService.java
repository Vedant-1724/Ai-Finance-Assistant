package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.dto.CreateTransactionRequest;
import com.financeassistant.financeassistant.dto.TransactionDTO;
import com.financeassistant.financeassistant.entity.Company;
import com.financeassistant.financeassistant.entity.Transaction;
import com.financeassistant.financeassistant.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository     transactionRepository;
    private final TransactionEventPublisher eventPublisher;

    // ── Get all transactions for a company ────────────────────────────────────
    public List<TransactionDTO> getTransactions(Long companyId) {
        return transactionRepository
                .findByCompanyId(companyId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // ── Create and save a new transaction ─────────────────────────────────────
    @Transactional
    @CacheEvict(value = "pnl", allEntries = true)
    public TransactionDTO createTransaction(Long companyId,
                                            CreateTransactionRequest request) {
        log.info("Creating transaction companyId={} amount={}",
                companyId, request.getAmount());

        // Use company reference by ID only — avoids needing @Builder on Company
        Company company = new Company();
        company.setId(companyId);

        Transaction txn = new Transaction();
        txn.setCompany(company);
        txn.setDate(request.getDate());
        txn.setAmount(request.getAmount());
        txn.setDescription(request.getDescription());
        txn.setSource("MANUAL");

        Transaction saved = transactionRepository.save(txn);
        log.info("Saved transaction id={}", saved.getId());

        // Publish event to RabbitMQ for async AI anomaly detection
        // Wrapped in try/catch — RabbitMQ being down must NEVER block saving
        try {
            eventPublisher.publishNewTransaction(companyId, saved.getId());
        } catch (Exception e) {
            log.warn("RabbitMQ publish skipped (non-critical): {}", e.getMessage());
        }

        return toDTO(saved);
    }

    // ── Entity → DTO mapping ──────────────────────────────────────────────────
    private TransactionDTO toDTO(Transaction txn) {
        TransactionDTO dto = new TransactionDTO();
        dto.setId(txn.getId());
        dto.setDate(txn.getDate() != null ? txn.getDate().toString() : null);
        dto.setAmount(txn.getAmount());
        dto.setDescription(txn.getDescription());
        dto.setCategoryName(
                txn.getCategory() != null ? txn.getCategory().getName() : null
        );
        return dto;
    }
}
