package com.financeassistant.financeassistant.repository;

import com.financeassistant.financeassistant.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

        List<Transaction> findByCompanyIdOrderByDateDesc(Long companyId);

        @org.springframework.data.jpa.repository.Modifying
        @org.springframework.data.jpa.repository.Query("DELETE FROM Transaction t WHERE t.company.id = :companyId")
        void deleteByCompanyId(@org.springframework.data.repository.query.Param("companyId") Long companyId);

        boolean existsByCompany_IdAndDateAndAmountAndDescriptionAndSource(
                        Long companyId,
                        LocalDate date,
                        BigDecimal amount,
                        String description,
                        String source);

        @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                        "WHERE t.company.id = :companyId " +
                        "AND t.amount > 0 " +
                        "AND t.date >= :startDate " +
                        "AND t.date <= :endDate")
        BigDecimal sumIncome(
                        @Param("companyId") Long companyId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

        @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                        "WHERE t.company.id = :companyId " +
                        "AND t.amount < 0 " +
                        "AND t.date >= :startDate " +
                        "AND t.date <= :endDate")
        BigDecimal sumExpense(
                        @Param("companyId") Long companyId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

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
                        @Param("endDate") LocalDate endDate);

        @Query("SELECT COUNT(t) FROM Transaction t " +
                        "WHERE t.company.id = :companyId " +
                        "AND t.date >= :startDate " +
                        "AND t.date <= :endDate")
        Long countByPeriod(
                        @Param("companyId") Long companyId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);
}
