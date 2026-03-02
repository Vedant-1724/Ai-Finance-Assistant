package com.financeassistant.financeassistant.service;

// PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/financeassistant/service/FinancialHealthService.java

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financeassistant.financeassistant.entity.FinancialHealthScore;
import com.financeassistant.financeassistant.repository.FinancialHealthScoreRepository;
import com.financeassistant.financeassistant.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialHealthService {

    private final TransactionRepository txnRepo;
    private final FinancialHealthScoreRepository scoreRepo;
    private final ObjectMapper objectMapper;

    /**
     * Compute or retrieve the health score for the given company and month.
     * Scores are cached in the DB — if already computed for this month, return it.
     */
    public FinancialHealthScore getOrComputeScore(Long companyId, LocalDate month) {
        LocalDate firstDay = month.withDayOfMonth(1);
        return scoreRepo.findByCompanyIdAndMonth(companyId, firstDay)
                .orElseGet(() -> computeAndSave(companyId, firstDay));
    }

    public List<FinancialHealthScore> getHistory(Long companyId) {
        return scoreRepo.findTop6ByCompanyIdOrderByMonthDesc(companyId);
    }

    private FinancialHealthScore computeAndSave(Long companyId, LocalDate firstDay) {
        LocalDate lastDay = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        // Previous month for comparison
        LocalDate prevFirst = firstDay.minusMonths(1);
        LocalDate prevLast  = prevFirst.withDayOfMonth(prevFirst.lengthOfMonth());

        BigDecimal income   = txnRepo.sumIncome(companyId, firstDay, lastDay);
        BigDecimal expenseR = txnRepo.sumExpense(companyId, firstDay, lastDay);
        BigDecimal expense  = expenseR.abs();
        BigDecimal net      = income.subtract(expense);

        BigDecimal prevExpense = txnRepo.sumExpense(companyId, prevFirst, prevLast).abs();

        // ── Scoring (out of 100) ──────────────────────────────────────────────
        // 1. Profit margin (30 pts)
        int marginScore = 0;
        if (income.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal margin = net.multiply(BigDecimal.valueOf(100)).divide(income, 2, RoundingMode.HALF_UP);
            marginScore = margin.compareTo(BigDecimal.valueOf(30)) >= 0 ? 30 :
                          margin.compareTo(BigDecimal.ZERO) >= 0 ? margin.intValue() : 0;
        }

        // 2. Expense growth (20 pts) — lower growth = better
        int growthScore = 20;
        if (prevExpense.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal growth = expense.subtract(prevExpense)
                    .multiply(BigDecimal.valueOf(100)).divide(prevExpense, 2, RoundingMode.HALF_UP);
            if (growth.compareTo(BigDecimal.valueOf(50)) > 0) growthScore = 0;
            else if (growth.compareTo(BigDecimal.valueOf(20)) > 0) growthScore = 10;
            else if (growth.compareTo(BigDecimal.ZERO) > 0) growthScore = 15;
        }

        // 3. Has income this month (25 pts)
        int incomeScore = income.compareTo(BigDecimal.ZERO) > 0 ? 25 : 0;

        // 4. Positive net (25 pts)
        int netScore = net.compareTo(BigDecimal.ZERO) >= 0 ? 25 : 0;

        int totalScore = Math.min(100, marginScore + growthScore + incomeScore + netScore);

        // ── Build breakdown JSON ──────────────────────────────────────────────
        String breakdown;
        try {
            breakdown = objectMapper.writeValueAsString(Map.of(
                    "profitMarginScore", marginScore,
                    "expenseGrowthScore", growthScore,
                    "incomeScore", incomeScore,
                    "netScore", netScore,
                    "totalIncome", income,
                    "totalExpense", expense,
                    "netProfit", net
            ));
        } catch (Exception e) {
            breakdown = "{}";
        }

        // ── Simple rule-based recommendations (fallback if AI unavailable) ────
        String recs = generateRecommendations(marginScore, growthScore, income, expense, net);

        FinancialHealthScore score = new FinancialHealthScore();
        score.setCompanyId(companyId);
        score.setMonth(firstDay);
        score.setScore(totalScore);
        score.setBreakdown(breakdown);
        score.setRecommendations(recs);

        return scoreRepo.save(score);
    }

    private String generateRecommendations(int marginScore, int growthScore,
                                            BigDecimal income, BigDecimal expense, BigDecimal net) {
        StringBuilder sb = new StringBuilder();
        if (net.compareTo(BigDecimal.ZERO) < 0) {
            sb.append("• Your expenses exceeded income this month. Review your top spending categories and identify cuts.\n");
        }
        if (marginScore < 15) {
            sb.append("• Your profit margin is below 15%. Consider raising prices or reducing variable costs.\n");
        }
        if (growthScore < 15) {
            sb.append("• Expenses grew significantly vs last month. Audit recurring subscriptions and vendor contracts.\n");
        }
        if (income.compareTo(BigDecimal.ZERO) == 0) {
            sb.append("• No income recorded this month. Add your revenue transactions to get accurate insights.\n");
        }
        if (sb.length() == 0) {
            sb.append("• Great financial health! Keep maintaining your expense discipline.\n");
            sb.append("• Consider setting aside 20% of net profit as emergency reserve.\n");
        }
        return sb.toString().trim();
    }
}
