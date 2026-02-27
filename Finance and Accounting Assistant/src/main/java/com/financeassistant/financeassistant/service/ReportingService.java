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

/**
 * FIXES vs old finance-backend version:
 * 1. getPnL() → getPnLReport() — matches ReportingController and TransactionService.
 * 2. Old cache key used '#month' (non-existent param). Fixed to '#period'.
 * 3. Old cache name was 'pnl-report'. Changed to 'pnl' (matches CacheConfig).
 * 4. invalidateReports() → evictPnLCache() — matches what TransactionService calls.
 * 5. Added full date range resolution (month / quarter / year / YYYY-MM).
 * 6. Added category breakdown building from Object[] rows returned by repo.
 * 7. expense stored as negative in DB — call .abs() before returning to UI.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportingService {

    private final TransactionRepository transactionRepository;

    // ── Main P&L Report (in-memory / Redis cached) ────────────────────────────
    @Cacheable(value = "pnl", key = "#companyId + '_' + #period")
    public PnLReport getPnLReport(Long companyId, String period) {
        log.info("Computing P&L report for company={} period={}", companyId, period);

        DateRange range = resolveDateRange(period);

        BigDecimal income          = transactionRepository.sumIncome(companyId, range.start, range.end);
        BigDecimal expenseRaw      = transactionRepository.sumExpense(companyId, range.start, range.end);
        BigDecimal positiveExpense = expenseRaw.abs();   // stored negative → display positive
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

    // ── Evict entire 'pnl' cache for this company after a new transaction ─────
    @CacheEvict(value = "pnl", allEntries = true)
    public void evictPnLCache(Long companyId) {
        log.debug("Evicted pnl cache for company={}", companyId);
    }

    // ── Date range resolution ─────────────────────────────────────────────────
    private DateRange resolveDateRange(String period) {
        LocalDate today = LocalDate.now();

        return switch (period) {
            case "month" -> new DateRange(
                    today.withDayOfMonth(1),
                    today.withDayOfMonth(today.lengthOfMonth())
            );
            case "quarter" -> {
                int month = today.getMonthValue();
                int quarterStartMonth = ((month - 1) / 3) * 3 + 1;
                LocalDate start = today.withMonth(quarterStartMonth).withDayOfMonth(1);
                LocalDate end   = start.plusMonths(3).minusDays(1);
                yield new DateRange(start, end);
            }
            case "year" -> new DateRange(
                    today.withDayOfYear(1),
                    today.withDayOfYear(today.lengthOfYear())
            );
            default -> {
                // Format: "2026-02" — specific month
                try {
                    LocalDate parsed = LocalDate.parse(period + "-01",
                            DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    yield new DateRange(
                            parsed,
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

    // ── Convert Object[] rows from sumByCategory into CategoryBreakdown list ──
    private List<CategoryBreakdown> buildBreakdown(List<Object[]> rows) {
        List<CategoryBreakdown> result = new ArrayList<>();
        if (rows == null) return result;

        for (Object[] row : rows) {
            String name     = row[0] != null ? row[0].toString() : "Uncategorized";
            BigDecimal amt  = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
            result.add(new CategoryBreakdown(name, amt));
        }
        return result;
    }

    // ── Simple record to hold date range ─────────────────────────────────────
    private record DateRange(LocalDate start, LocalDate end) {}
}