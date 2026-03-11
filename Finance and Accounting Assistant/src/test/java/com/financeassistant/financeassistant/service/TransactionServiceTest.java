package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.dto.CreateTransactionRequest;
import com.financeassistant.financeassistant.entity.Company;
import com.financeassistant.financeassistant.entity.Transaction;
import com.financeassistant.financeassistant.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionEventPublisher eventPublisher;

    @Mock
    private ReportingService reportingService;

    @Mock
    private AuditService auditService;

    @Mock
    private EntityManager entityManager;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService();
        ReflectionTestUtils.setField(transactionService, "repo", transactionRepository);
        ReflectionTestUtils.setField(transactionService, "eventPublisher", eventPublisher);
        ReflectionTestUtils.setField(transactionService, "reportingService", reportingService);
        ReflectionTestUtils.setField(transactionService, "auditService", auditService);
        ReflectionTestUtils.setField(transactionService, "em", entityManager);
    }

    @Test
    void createTransactionRejectsDuplicate() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setDate(LocalDate.of(2026, 3, 11));
        request.setAmount(new BigDecimal("-850.00"));
        request.setDescription("  Coffee   purchase  ");

        when(transactionRepository.existsByCompany_IdAndDateAndAmountAndDescriptionIgnoreCase(
                1L,
                LocalDate.of(2026, 3, 11),
                new BigDecimal("-850.00"),
                "Coffee purchase")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transactionService.createTransaction(1L, request, null, null));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("A transaction with the same date, amount, and description already exists.", ex.getReason());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void updateTransactionRejectsDuplicateFromAnotherRow() {
        Company company = new Company(1L, 1L, "Joshi", "INR", LocalDateTime.now());
        Transaction existing = Transaction.builder()
                .id(10L)
                .company(company)
                .date(LocalDate.of(2026, 3, 10))
                .amount(new BigDecimal("-450.00"))
                .description("Office lunch")
                .type(Transaction.TransactionType.EXPENSE)
                .source("MANUAL")
                .build();

        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setDate(LocalDate.of(2026, 3, 11));
        request.setAmount(new BigDecimal("-850.00"));
        request.setDescription("Coffee purchase");

        when(transactionRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(transactionRepository.existsByCompany_IdAndDateAndAmountAndDescriptionIgnoreCaseAndIdNot(
                1L,
                LocalDate.of(2026, 3, 11),
                new BigDecimal("-850.00"),
                "Coffee purchase",
                10L)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transactionService.updateTransaction(1L, 10L, request, null, null));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }
}
