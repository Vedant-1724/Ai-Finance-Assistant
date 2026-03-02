package com.financeassistant.financeassistant.service;

// PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/financeassistant/service/BudgetService.java

import com.financeassistant.financeassistant.entity.Budget;
import com.financeassistant.financeassistant.entity.Company;
import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.repository.BudgetRepository;
import com.financeassistant.financeassistant.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepo;
    private final TransactionRepository txnRepo;
    private final EmailAlertService emailAlertService;

    @PersistenceContext
    private EntityManager em;

    // ── Create or update a budget ─────────────────────────────────────────────
    @Transactional
    public BudgetDTO upsertBudget(Long companyId, UpsertBudgetRequest req) {
        LocalDate month = LocalDate.of(req.year(), req.month(), 1);

        Budget budget = budgetRepo.findByCompanyAndMonthAndCategory(companyId, month, req.categoryId())
                .orElseGet(Budget::new);

        budget.setCompany(em.getReference(Company.class, companyId));
        budget.setMonth(month);
        budget.setAmount(req.amount());
        if (req.categoryId() != null) {
            budget.setCategory(em.getReference(com.financeassistant.financeassistant.entity.Category.class, req.categoryId()));
        }

        Budget saved = budgetRepo.save(budget);
        return toDTO(saved, BigDecimal.ZERO);
    }

    // ── Get variance for a month ──────────────────────────────────────────────
    public List<BudgetVarianceDTO> getVariance(Long companyId, int year, int month) {
        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        List<Budget> budgets = budgetRepo.findByCompanyIdAndMonthOrderByCategoryIdAsc(companyId, firstDay);

        // Get actual spend per category
        List<Object[]> actuals = txnRepo.sumByCategory(companyId, firstDay, lastDay);
        Map<String, BigDecimal> actualMap = actuals.stream()
                .collect(Collectors.toMap(
                        r -> r[0] != null ? (String) r[0] : "Uncategorised",
                        r -> ((BigDecimal) r[1]).abs()
                ));

        List<BudgetVarianceDTO> result = new ArrayList<>();
        for (Budget b : budgets) {
            String catName = b.getCategory() != null ? b.getCategory().getName() : "Overall";
            BigDecimal actual = actualMap.getOrDefault(catName, BigDecimal.ZERO);
            BigDecimal variance = b.getAmount().subtract(actual);
            int pct = b.getAmount().compareTo(BigDecimal.ZERO) == 0 ? 0 :
                      actual.multiply(BigDecimal.valueOf(100))
                            .divide(b.getAmount(), 0, RoundingMode.HALF_UP).intValue();

            result.add(new BudgetVarianceDTO(
                    b.getId(), catName, b.getAmount(), actual, variance, pct,
                    pct >= 100 ? "OVER" : pct >= 90 ? "WARNING" : "OK"
            ));
        }
        return result;
    }

    // ── Check budgets and trigger alerts (called by scheduler) ───────────────
    public void checkAndAlertBudgets(Long companyId, User user) {
        int yr = LocalDate.now().getYear();
        int mo = LocalDate.now().getMonthValue();
        List<BudgetVarianceDTO> variances = getVariance(companyId, yr, mo);
        for (BudgetVarianceDTO v : variances) {
            if (v.percentage() >= 90) {
                emailAlertService.sendBudgetAlert(user, v.categoryName(), v.actual(), v.budgeted());
            }
        }
    }

    private BudgetDTO toDTO(Budget b, BigDecimal actual) {
        return new BudgetDTO(b.getId(),
                b.getCategory() != null ? b.getCategory().getName() : "Overall",
                b.getCategory() != null ? b.getCategory().getId() : null,
                b.getMonth().getYear(), b.getMonth().getMonthValue(),
                b.getAmount(), actual);
    }

    // ── DTOs (records) ────────────────────────────────────────────────────────
    public record UpsertBudgetRequest(int year, int month, Long categoryId, BigDecimal amount) {}

    public record BudgetDTO(Long id, String categoryName, Long categoryId,
                            int year, int month, BigDecimal amount, BigDecimal spent) {}

    public record BudgetVarianceDTO(Long id, String categoryName,
                                    BigDecimal budgeted, BigDecimal actual,
                                    BigDecimal variance, int percentage, String status) {}
}
