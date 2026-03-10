package com.financeassistant.financeassistant.service;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialHealthService {

    private final TransactionRepository txnRepo;
    private final FinancialHealthScoreRepository scoreRepo;
    private final ObjectMapper objectMapper;

    public FinancialHealthScore getOrComputeScore(Long companyId, LocalDate month) {
        LocalDate firstDay = month.withDayOfMonth(1);
        return scoreRepo.findByCompanyIdAndMonth(companyId, firstDay)
                .orElseGet(() -> computeAndSave(companyId, firstDay));
    }

    public List<FinancialHealthScore> getHistory(Long companyId) {
        return scoreRepo.findTop6ByCompanyIdOrderByMonthDesc(companyId);
    }

    public Integer getPreviousScore(Long companyId, LocalDate month) {
        return scoreRepo.findTop1ByCompanyIdAndMonthBeforeOrderByMonthDesc(companyId, month.withDayOfMonth(1))
                .map(FinancialHealthScore::getScore)
                .orElse(null);
    }

    private FinancialHealthScore computeAndSave(Long companyId, LocalDate firstDay) {
        LocalDate lastDay = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
        LocalDate prevFirst = firstDay.minusMonths(1);
        LocalDate prevLast = prevFirst.withDayOfMonth(prevFirst.lengthOfMonth());

        BigDecimal income = txnRepo.sumIncome(companyId, firstDay, lastDay);
        BigDecimal expenseRaw = txnRepo.sumExpense(companyId, firstDay, lastDay);
        BigDecimal expense = expenseRaw.abs();
        BigDecimal net = income.subtract(expense);

        BigDecimal prevExpense = txnRepo.sumExpense(companyId, prevFirst, prevLast).abs();

        int marginScore = 0;
        if (income.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal margin = net.multiply(BigDecimal.valueOf(100)).divide(income, 2, RoundingMode.HALF_UP);
            marginScore = margin.compareTo(BigDecimal.valueOf(30)) >= 0 ? 30
                    : margin.compareTo(BigDecimal.ZERO) >= 0 ? margin.intValue() : 0;
        }

        int growthScore = 20;
        if (prevExpense.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal growth = expense.subtract(prevExpense)
                    .multiply(BigDecimal.valueOf(100)).divide(prevExpense, 2, RoundingMode.HALF_UP);
            if (growth.compareTo(BigDecimal.valueOf(50)) > 0) {
                growthScore = 0;
            } else if (growth.compareTo(BigDecimal.valueOf(20)) > 0) {
                growthScore = 10;
            } else if (growth.compareTo(BigDecimal.ZERO) > 0) {
                growthScore = 15;
            }
        }

        int incomeScore = income.compareTo(BigDecimal.ZERO) > 0 ? 25 : 0;
        int netScore = net.compareTo(BigDecimal.ZERO) >= 0 ? 25 : 0;
        int totalScore = Math.min(100, marginScore + growthScore + incomeScore + netScore);

        List<ScoreBreakdown> breakdownItems = List.of(
                new ScoreBreakdown(
                        "Profit Margin",
                        toPercent(marginScore, 30),
                        30,
                        income.compareTo(BigDecimal.ZERO) > 0
                                ? "Net margin for the month based on income vs expenses."
                                : "Add income transactions to calculate profit margin."),
                new ScoreBreakdown(
                        "Expense Growth",
                        toPercent(growthScore, 20),
                        20,
                        prevExpense.compareTo(BigDecimal.ZERO) > 0
                                ? "Compares current month expenses with last month."
                                : "No prior expense baseline available, so this section starts neutral."),
                new ScoreBreakdown(
                        "Income Activity",
                        toPercent(incomeScore, 25),
                        25,
                        income.compareTo(BigDecimal.ZERO) > 0
                                ? "Income was recorded during the selected month."
                                : "No income recorded this month."),
                new ScoreBreakdown(
                        "Positive Cash Flow",
                        toPercent(netScore, 25),
                        25,
                        net.compareTo(BigDecimal.ZERO) >= 0
                                ? "Income covered expenses this month."
                                : "Expenses exceeded income this month."));

        String breakdown;
        try {
            breakdown = objectMapper.writeValueAsString(breakdownItems);
        } catch (Exception e) {
            breakdown = "[]";
        }

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
        if (expense.compareTo(BigDecimal.ZERO) == 0 && income.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("• Expenses are very low compared with income. Double-check that all recurring bills are being captured.\n");
        }
        if (sb.length() == 0) {
            sb.append("• Great financial health! Keep maintaining your expense discipline.\n");
            sb.append("• Consider setting aside 20% of net profit as emergency reserve.\n");
        }
        return sb.toString().trim();
    }

    private int toPercent(int score, int maxScore) {
        if (maxScore == 0) {
            return 0;
        }
        return (int) Math.round((score * 100.0d) / maxScore);
    }

    private record ScoreBreakdown(String label, int score, int weight, String detail) {
    }
}
