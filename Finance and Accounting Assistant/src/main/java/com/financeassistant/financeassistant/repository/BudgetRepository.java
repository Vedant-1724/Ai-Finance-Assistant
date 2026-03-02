package com.financeassistant.financeassistant.repository;

// PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/financeassistant/repository/BudgetRepository.java

import com.financeassistant.financeassistant.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByCompanyIdAndMonthOrderByCategoryIdAsc(Long companyId, LocalDate month);

    @Query("SELECT b FROM Budget b WHERE b.company.id = :cid AND b.month = :month AND " +
           "(b.category IS NULL AND :catId IS NULL OR b.category.id = :catId)")
    Optional<Budget> findByCompanyAndMonthAndCategory(
            @Param("cid") Long companyId,
            @Param("month") LocalDate month,
            @Param("catId") Long categoryId);

    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM Budget b " +
           "WHERE b.company.id = :cid AND b.month = :month")
    java.math.BigDecimal sumByCompanyAndMonth(
            @Param("cid") Long companyId, @Param("month") LocalDate month);
}
