package com.financeassistant.financeassistant.repository;

import com.financeassistant.financeassistant.entity.FinancialHealthScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FinancialHealthScoreRepository extends JpaRepository<FinancialHealthScore, Long> {
    Optional<FinancialHealthScore> findByCompanyIdAndMonth(Long companyId, LocalDate month);
    Optional<FinancialHealthScore> findTop1ByCompanyIdAndMonthBeforeOrderByMonthDesc(Long companyId, LocalDate month);
    List<FinancialHealthScore> findTop6ByCompanyIdOrderByMonthDesc(Long companyId);
}
