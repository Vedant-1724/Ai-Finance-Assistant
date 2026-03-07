package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.dto.TransactionDTO;
import com.financeassistant.financeassistant.entity.Transaction;
import com.financeassistant.financeassistant.repository.TransactionRepository;
import com.financeassistant.financeassistant.entity.Transaction.TransactionType;
import com.financeassistant.financeassistant.repository.CompanyRepository;
import com.financeassistant.financeassistant.entity.Company;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mock Service for Setu AA Bank Sync.
 * Simulates the RBI Account Aggregator flow where a user consents to share bank
 * data,
 * and the system fetches recent transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SetuBankService {

  private final TransactionRepository transactionRepository;
  private final CompanyRepository companyRepository;

  public String createConsentLink(Long companyId) {
    log.info("Creating Setu AA consent link for company: {}", companyId);
    // In reality, this would call the Setu /consents API
    // For the mock, we just return a fake URL that the frontend redirects to
    return "https://mock-aa.setu.co/consent?id=" + UUID.randomUUID().toString();
  }

  public List<TransactionDTO> syncTransactions(Long companyId) {
    log.info("Syncing transactions from Setu AA for company: {}", companyId);

    // Mocked response representing recent bank activity pulled via AA
    List<Transaction> mockBankTxns = new ArrayList<>();

    // Generate a few random recent transactions
    LocalDate today = LocalDate.now();
    mockBankTxns.add(createTxn(companyId, today.minusDays(1), "Zomato Meals", new BigDecimal("-850.00")));
    mockBankTxns.add(createTxn(companyId, today.minusDays(2), "AWS Cloud Services", new BigDecimal("-4500.00")));
    mockBankTxns.add(createTxn(companyId, today.minusDays(3), "Client Payment ZXC", new BigDecimal("25000.00")));
    mockBankTxns.add(createTxn(companyId, today.minusDays(5), "Uber Rides", new BigDecimal("-1200.00")));
    mockBankTxns.add(createTxn(companyId, today.minusDays(6), "Office Supplies", new BigDecimal("-3200.00")));

    // Save them to DB
    List<Transaction> saved = transactionRepository.saveAll(mockBankTxns);

    return saved.stream().map(t -> new TransactionDTO(
        t.getId(),
        t.getDate().toString(),
        t.getAmount(),
        t.getDescription(),
        null)).toList();
  }

  private Transaction createTxn(Long companyId, LocalDate date, String desc, BigDecimal amount) {
    Transaction t = new Transaction();
    Company company = companyRepository.getReferenceById(companyId);
    t.setCompany(company);
    t.setDate(date);
    t.setDescription(desc);
    t.setAmount(amount);
    t.setType(amount.compareTo(BigDecimal.ZERO) < 0 ? TransactionType.EXPENSE : TransactionType.INCOME);
    t.setSource("Setu AA");
    return t;
  }
}
