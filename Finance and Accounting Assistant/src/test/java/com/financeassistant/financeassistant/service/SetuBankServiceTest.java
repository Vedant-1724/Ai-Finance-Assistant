package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.dto.BankSyncResultDto;
import com.financeassistant.financeassistant.entity.BankSyncConsent;
import com.financeassistant.financeassistant.entity.Company;
import com.financeassistant.financeassistant.entity.Transaction;
import com.financeassistant.financeassistant.repository.BankSyncConsentRepository;
import com.financeassistant.financeassistant.repository.CompanyRepository;
import com.financeassistant.financeassistant.repository.TransactionRepository;
import com.financeassistant.financeassistant.service.banksync.BankSyncProvider;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetuBankServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private BankSyncConsentRepository bankSyncConsentRepository;

    @Mock
    private BankSyncProvider bankSyncProvider;

    @Mock
    private TransactionEventPublisher eventPublisher;

    @Mock
    private ReportingService reportingService;

    private SetuBankService setuBankService;

    @BeforeEach
    void setUp() {
        setuBankService = new SetuBankService(
                transactionRepository,
                companyRepository,
                bankSyncConsentRepository,
                List.of(bankSyncProvider),
                eventPublisher,
                reportingService
        );
        ReflectionTestUtils.setField(setuBankService, "configuredProvider", "AUTO");
    }

    @Test
    void syncTransactionsReturnsStructuredDuplicateAwareResult() {
        Company company = new Company(1L, 1L, "Joshi", "INR", LocalDateTime.now());
        BankSyncConsent consent = BankSyncConsent.builder()
                .id(7L)
                .companyId(1L)
                .providerKey("MOCK")
                .status(BankSyncConsent.Status.CONSENT_GRANTED)
                .stateToken("abc")
                .consentExpiresAt(LocalDateTime.now().plusDays(1))
                .mockFallback(true)
                .build();

        BankSyncProvider.FetchedTransaction first = new BankSyncProvider.FetchedTransaction(
                LocalDate.of(2026, 3, 10),
                new BigDecimal("-850.00"),
                "Zomato Meals",
                "REF-1",
                "Main Account",
                "Setu AA (Mock)"
        );
        BankSyncProvider.FetchedTransaction duplicate = new BankSyncProvider.FetchedTransaction(
                LocalDate.of(2026, 3, 10),
                new BigDecimal("-850.00"),
                "Zomato Meals",
                "REF-2",
                "Main Account",
                "Setu AA (Mock)"
        );

        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(bankSyncConsentRepository.findTopByCompanyIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(consent));
        when(bankSyncProvider.getKey()).thenReturn("MOCK");
        when(bankSyncProvider.syncTransactions(company, consent)).thenReturn(new BankSyncProvider.SyncPayload(List.of(first, duplicate), "demo sync"));
        when(transactionRepository.existsByCompany_IdAndDateAndAmountAndDescriptionIgnoreCase(
                1L,
                LocalDate.of(2026, 3, 10),
                new BigDecimal("-850.00"),
                "Zomato Meals")).thenReturn(false);
        when(transactionRepository.saveAll(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Transaction> transactions = invocation.getArgument(0);
            long id = 200L;
            for (Transaction transaction : transactions) {
                transaction.setId(id++);
            }
            return transactions;
        });

        BankSyncResultDto result = setuBankService.syncTransactions(1L);

        assertEquals(1, result.imported());
        assertEquals(1, result.duplicates());
        assertTrue(result.message().contains("skipped 1 duplicate"));

        ArgumentCaptor<List<Transaction>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository).saveAll(batchCaptor.capture());
        Transaction saved = batchCaptor.getValue().get(0);
        assertEquals("BANK_SYNC", saved.getSource());
        assertEquals(Transaction.TransactionType.EXPENSE, saved.getType());
        verify(eventPublisher).publishTransactionEvent(1L, List.of(200L));
        verify(reportingService).evictPnLCache(1L);
        verify(bankSyncConsentRepository).save(consent);
    }
}

