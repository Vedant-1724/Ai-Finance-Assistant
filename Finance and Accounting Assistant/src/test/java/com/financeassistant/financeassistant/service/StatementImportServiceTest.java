package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.dto.BulkImportRequest;
import com.financeassistant.financeassistant.dto.ImportTransactionsResultDto;
import com.financeassistant.financeassistant.entity.Company;
import com.financeassistant.financeassistant.entity.Transaction;
import com.financeassistant.financeassistant.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementImportServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionEventPublisher eventPublisher;

    @Mock
    private ReportingService reportingService;

    @Mock
    private EntityManager entityManager;

    private StatementImportService statementImportService;

    @BeforeEach
    void setUp() {
        statementImportService = new StatementImportService(transactionRepository, eventPublisher, reportingService);
        ReflectionTestUtils.setField(statementImportService, "em", entityManager);
    }

    @Test
    void importTransactionsNormalizesSourceSetsTypeAndSkipsDuplicates() {
        Company company = new Company(1L, 1L, "Joshi", "INR", LocalDateTime.now());
        when(entityManager.getReference(Company.class, 1L)).thenReturn(company);
        when(transactionRepository.existsByCompany_IdAndDateAndAmountAndDescriptionIgnoreCase(
                1L,
                LocalDate.of(2026, 3, 1),
                new BigDecimal("-850.00"),
                "Coffee purchase")).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            transaction.setId(101L);
            return transaction;
        });

        BulkImportRequest request = new BulkImportRequest();
        BulkImportRequest.ImportItem first = new BulkImportRequest.ImportItem();
        first.setDate(LocalDate.of(2026, 3, 1));
        first.setDescription("Coffee purchase");
        first.setAmount(new BigDecimal("-850.00"));
        first.setSource("PDF_STATEMENT");

        BulkImportRequest.ImportItem duplicate = new BulkImportRequest.ImportItem();
        duplicate.setDate(LocalDate.of(2026, 3, 1));
        duplicate.setDescription("Coffee purchase");
        duplicate.setAmount(new BigDecimal("-850.00"));
        duplicate.setSource("PDF_STATEMENT");

        BulkImportRequest.ImportItem invalid = new BulkImportRequest.ImportItem();
        invalid.setDate(LocalDate.of(2026, 3, 2));
        invalid.setDescription("Zero row");
        invalid.setAmount(BigDecimal.ZERO);
        invalid.setSource("weird-source");

        request.setTransactions(List.of(first, duplicate, invalid));

        ImportTransactionsResultDto result = statementImportService.importTransactions(1L, request);

        assertEquals(1, result.imported());
        assertEquals(1, result.duplicates());
        assertEquals(1, result.skipped());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("legacy source 'PDF_STATEMENT'")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("amount cannot be zero")));

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        Transaction saved = transactionCaptor.getValue();
        assertEquals("PDF", saved.getSource());
        assertEquals(Transaction.TransactionType.EXPENSE, saved.getType());
        verify(eventPublisher).publishTransactionEvent(1L, List.of(101L));
        verify(reportingService).evictPnLCache(1L);
    }

    @Test
    void importTransactionsDoesNotPublishEventsWhenNothingIsImported() {
        Company company = new Company(1L, 1L, "Joshi", "INR", LocalDateTime.now());
        when(entityManager.getReference(Company.class, 1L)).thenReturn(company);

        BulkImportRequest request = new BulkImportRequest();
        BulkImportRequest.ImportItem invalid = new BulkImportRequest.ImportItem();
        invalid.setDate(LocalDate.of(2026, 3, 2));
        invalid.setDescription("Zero row");
        invalid.setAmount(BigDecimal.ZERO);
        request.setTransactions(List.of(invalid));

        ImportTransactionsResultDto result = statementImportService.importTransactions(1L, request);

        assertEquals(0, result.imported());
        assertEquals(1, result.skipped());
        verify(eventPublisher, never()).publishTransactionEvent(any(), any());
        verify(reportingService, never()).evictPnLCache(any());
    }
}

