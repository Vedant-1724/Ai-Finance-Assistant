package com.financeassistant.financeassistant.service;

// PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/financeassistant/service/ChartDataService.java

import com.financeassistant.financeassistant.entity.Transaction;
import com.financeassistant.financeassistant.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChartDataService {

    private final TransactionRepository txnRepo;
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMM yy");

    @Cacheable(value = "chart-data", key = "#companyId + '_' + #months")
    public ChartDataResponse getChartData(Long companyId, int months) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusMonths(months - 1).withDayOfMonth(1);

        List<Transaction> txns = txnRepo.findByCompanyIdOrderByDateDesc(companyId).stream()
                .filter(t -> !t.getDate().isBefore(start))
                .toList();

        // ── Monthly income vs expense ─────────────────────────────────────────
        Map<String, BigDecimal> monthlyIncome  = new LinkedHashMap<>();
        Map<String, BigDecimal> monthlyExpense = new LinkedHashMap<>();

        // Seed all months with zero
        LocalDate cur = start;
        while (!cur.isAfter(today)) {
            String key = cur.format(MONTH_FMT);
            monthlyIncome.put(key, BigDecimal.ZERO);
            monthlyExpense.put(key, BigDecimal.ZERO);
            cur = cur.plusMonths(1);
        }

        for (Transaction t : txns) {
            String key = t.getDate().withDayOfMonth(1).format(MONTH_FMT);
            if (t.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                monthlyIncome.merge(key, t.getAmount(), BigDecimal::add);
            } else {
                monthlyExpense.merge(key, t.getAmount().abs(), BigDecimal::add);
            }
        }

        List<MonthlyBar> monthlyBars = monthlyIncome.entrySet().stream()
                .map(e -> new MonthlyBar(e.getKey(), e.getValue(),
                        monthlyExpense.getOrDefault(e.getKey(), BigDecimal.ZERO),
                        e.getValue().subtract(monthlyExpense.getOrDefault(e.getKey(), BigDecimal.ZERO))))
                .toList();

        // ── Category breakdown (expense only) ────────────────────────────────
        Map<String, BigDecimal> catTotals = new LinkedHashMap<>();
        for (Transaction t : txns) {
            if (t.getAmount().compareTo(BigDecimal.ZERO) >= 0) continue;
            String cat = t.getCategory() != null ? t.getCategory().getName() : "Other";
            catTotals.merge(cat, t.getAmount().abs(), BigDecimal::add);
        }
        BigDecimal totalExpense = catTotals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CategoryPie> pieData = catTotals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(8)
                .map(e -> new CategoryPie(e.getKey(), e.getValue(),
                        totalExpense.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                        e.getValue().multiply(BigDecimal.valueOf(100))
                                    .divide(totalExpense, 1, RoundingMode.HALF_UP).doubleValue()))
                .collect(Collectors.toList());

        // ── Daily running balance (last 60 days) ─────────────────────────────
        LocalDate balStart = today.minusDays(59);
        BigDecimal running = BigDecimal.ZERO;
        List<DailyBalance> dailyBalance = new ArrayList<>();

        // Sort ascending
        List<Transaction> sorted = txns.stream()
                .filter(t -> !t.getDate().isBefore(balStart))
                .sorted(Comparator.comparing(Transaction::getDate))
                .toList();

        Map<LocalDate, BigDecimal> dailyNet = new LinkedHashMap<>();
        for (Transaction t : sorted) {
            dailyNet.merge(t.getDate(), t.getAmount(), BigDecimal::add);
        }

        LocalDate d = balStart;
        while (!d.isAfter(today)) {
            running = running.add(dailyNet.getOrDefault(d, BigDecimal.ZERO));
            dailyBalance.add(new DailyBalance(d.toString(), running));
            d = d.plusDays(1);
        }

        return new ChartDataResponse(monthlyBars, pieData, dailyBalance);
    }

    // ── Response records ──────────────────────────────────────────────────────
    public record MonthlyBar(String month, BigDecimal income, BigDecimal expense, BigDecimal net) {}
    public record CategoryPie(String name, BigDecimal value, double percent) {}
    public record DailyBalance(String date, BigDecimal balance) {}
    public record ChartDataResponse(List<MonthlyBar> monthly, List<CategoryPie> categoryBreakdown, List<DailyBalance> dailyBalance) {}
}
