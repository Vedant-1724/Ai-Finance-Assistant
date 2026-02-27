package com.financeassistant.financeassistant.repository;

import com.financeassistant.financeassistant.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * FIXES vs old finance-backend version:
 * 1. Removed findByCompanyId(Long, Pageable) — replaced with findByCompanyIdOrderByDateDesc.
 * 2. Old totalIncome/totalExpense queries relied on category.type — which NPEs
 *    when category is null. Replaced with amount > 0 / amount < 0 logic and
 *    added date-range parameters needed by ReportingService.
 * 3. Added sumByCategory() for the breakdown section of PnL reports.
 * 4. Added countByPeriod() — useful for dashboard stats.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // ── Basic lookup — newest first ────────────────────────────────────────────
    List<Transaction> findByCompanyIdOrderByDateDesc(Long companyId);

    // ── P&L income aggregation (positive amounts = income) ────────────────────
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.company.id = :companyId " +
            "AND t.amount > 0 " +
            "AND t.date >= :startDate " +
            "AND t.date <= :endDate")
    BigDecimal sumIncome(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate
    );

    // ── P&L expense aggregation (negative amounts = expense) ─────────────────
    // Returns a negative value; ReportingService calls .abs() before display.
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.company.id = :companyId " +
            "AND t.amount < 0 " +
            "AND t.date >= :startDate " +
            "AND t.date <= :endDate")
    BigDecimal sumExpense(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate
    );

    // ── Category breakdown ────────────────────────────────────────────────────
    // Returns Object[] rows: [categoryName (String or null), amountSum (BigDecimal)]
    @Query("SELECT COALESCE(c.name, 'Uncategorized'), COALESCE(SUM(t.amount), 0) " +
            "FROM Transaction t LEFT JOIN t.category c " +
            "WHERE t.company.id = :companyId " +
            "AND t.date >= :startDate " +
            "AND t.date <= :endDate " +
            "GROUP BY c.name " +
            "ORDER BY SUM(t.amount) DESC")
    List<Object[]> sumByCategory(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate
    );

    // ── Transaction count for a period ────────────────────────────────────────
    @Query("SELECT COUNT(t) FROM Transaction t " +
            "WHERE t.company.id = :companyId " +
            "AND t.date >= :startDate " +
            "AND t.date <= :endDate")
    Long countByPeriod(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate
    );
}