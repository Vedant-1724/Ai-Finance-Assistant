package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.entity.Budget;
import com.financeassistant.financeassistant.entity.Category;
import com.financeassistant.financeassistant.repository.BudgetRepository;
import com.financeassistant.financeassistant.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private EmailAlertService emailAlertService;

    private BudgetService budgetService;

    @BeforeEach
    void setUp() {
        budgetService = new BudgetService(budgetRepository, transactionRepository, emailAlertService);
    }

    @Test
    void getVarianceUsesTotalExpenseForOverallBudget() {
        Budget budget = new Budget();
        budget.setId(1L);
        budget.setMonth(LocalDate.of(2026, 3, 1));
        budget.setAmount(BigDecimal.valueOf(10000));

        when(budgetRepository.findByCompanyIdAndMonthOrderByCategoryIdAsc(7L, LocalDate.of(2026, 3, 1)))
                .thenReturn(List.of(budget));
        when(transactionRepository.sumExpenseByCategory(7L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .thenReturn(List.of());
        when(transactionRepository.sumExpense(7L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .thenReturn(BigDecimal.valueOf(-3200));

        List<BudgetService.BudgetVarianceDTO> variances = budgetService.getVariance(7L, 2026, 3);

        assertEquals(1, variances.size());
        assertEquals("Overall", variances.get(0).categoryName());
        assertEquals(BigDecimal.valueOf(3200), variances.get(0).actual());
        assertEquals(BigDecimal.valueOf(6800), variances.get(0).variance());
        assertEquals(32, variances.get(0).percentage());
        assertEquals("OK", variances.get(0).status());
    }

    @Test
    void getVarianceUsesExpenseBreakdownForCategoryBudgets() {
        Category category = new Category();
        category.setId(5L);
        category.setName("Marketing");

        Budget budget = new Budget();
        budget.setId(2L);
        budget.setCategory(category);
        budget.setMonth(LocalDate.of(2026, 3, 1));
        budget.setAmount(BigDecimal.valueOf(5000));

        when(budgetRepository.findByCompanyIdAndMonthOrderByCategoryIdAsc(7L, LocalDate.of(2026, 3, 1)))
                .thenReturn(List.of(budget));
        when(transactionRepository.sumExpenseByCategory(7L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .thenReturn(List.<Object[]>of(new Object[] { "Marketing", BigDecimal.valueOf(4800) }));
        when(transactionRepository.sumExpense(7L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .thenReturn(BigDecimal.valueOf(-4800));

        List<BudgetService.BudgetVarianceDTO> variances = budgetService.getVariance(7L, 2026, 3);

        assertEquals(1, variances.size());
        assertEquals("Marketing", variances.get(0).categoryName());
        assertEquals(BigDecimal.valueOf(4800), variances.get(0).actual());
        assertEquals(BigDecimal.valueOf(200), variances.get(0).variance());
        assertEquals(96, variances.get(0).percentage());
        assertEquals("WARNING", variances.get(0).status());
    }
}
