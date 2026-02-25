package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.dto.PnLReport;
import com.financeassistant.financeassistant.dto.PnLReport.CategoryBreakdown;
import com.financeassistant.financeassistant.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportingService {

    private final TransactionRepository transactionRepository;

    // ── Main P&L Report (Redis-cached) ────────────────────────────────────────
    @Cacheable(value = "pnl", key = "#companyId + '_' + #period")
    public PnLReport getPnLReport(Long companyId, String period) {
        log.info("Computing P&L report for company={} period={}", companyId, period);

        DateRange range = resolveDateRange(period);

        BigDecimal income  = transactionRepository.sumIncome(companyId, range.start, range.end);
        BigDecimal expense = transactionRepository.sumExpense(companyId, range.start, range.end);

        // expense is stored as negative in DB — make it positive for display
        BigDecimal positiveExpense = expense.abs();
        BigDecimal netProfit       = income.subtract(positiveExpense);

        List<CategoryBreakdown> breakdown = buildBreakdown(
                transactionRepository.sumByCategory(companyId, range.start, range.end)
        );

        return PnLReport.builder()
                .period(period)
                .startDate(range.start.toString())
                .endDate(range.end.toString())
                .totalIncome(income)
                .totalExpense(positiveExpense)
                .netProfit(netProfit)
                .breakdown(breakdown)
                .build();
    }

    // ── Evict cache for a company (called after new transaction saved) ─────────
    @CacheEvict(value = "pnl", allEntries = true)
    public void evictPnLCache(Long companyId) {
        log.info("Evicted P&L cache for company={}", companyId);
    }

    // ── Period → date range resolver ──────────────────────────────────────────
    private DateRange resolveDateRange(String period) {
        LocalDate today = LocalDate.now();

        return switch (period.toLowerCase()) {
            case "month" -> new DateRange(
                    today.withDayOfMonth(1),
                    today.withDayOfMonth(today.lengthOfMonth())
            );
            case "quarter" -> {
                int currentQuarter = (today.getMonthValue() - 1) / 3;
                int startMonth     = currentQuarter * 3 + 1;
                LocalDate start    = LocalDate.of(today.getYear(), startMonth, 1);
                LocalDate end      = start.plusMonths(3).minusDays(1);
                yield new DateRange(start, end);
            }
            case "year" -> new DateRange(
                    LocalDate.of(today.getYear(), 1, 1),
                    LocalDate.of(today.getYear(), 12, 31)
            );
            default -> {
                // Accept specific "YYYY-MM" format, e.g. "2026-02"
                try {
                    LocalDate parsed = LocalDate.parse(period + "-01",
                            DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    yield new DateRange(
                            parsed.withDayOfMonth(1),
                            parsed.withDayOfMonth(parsed.lengthOfMonth())
                    );
                } catch (Exception e) {
                    log.warn("Unknown period '{}', defaulting to current month", period);
                    yield new DateRange(
                            today.withDayOfMonth(1),
                            today.withDayOfMonth(today.lengthOfMonth())
                    );
                }
            }
        };
    }

    // ── Build category breakdown list from raw query results ──────────────────
    private List<CategoryBreakdown> buildBreakdown(List<Object[]> rows) {
        List<CategoryBreakdown> list = new ArrayList<>();
        for (Object[] row : rows) {
            String     name   = row[0] != null ? row[0].toString() : "Uncategorized";
            BigDecimal amount = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
            String     type   = amount.compareTo(BigDecimal.ZERO) >= 0 ? "INCOME" : "EXPENSE";

            list.add(CategoryBreakdown.builder()
                    .categoryName(name)
                    .amount(amount.abs())   // always positive for display
                    .type(type)
                    .build());
        }
        return list;
    }

    // ── Inner helper record ────────────────────────────────────────────────────
    private record DateRange(LocalDate start, LocalDate end) {}
}
