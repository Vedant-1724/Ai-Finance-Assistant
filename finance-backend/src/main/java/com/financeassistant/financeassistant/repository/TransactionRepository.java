package com.financeassistant.financeassistant.repository;

import com.financeassistant.financeassistant.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByCompanyId(Long companyId, Pageable pageable);

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.company.id = :companyId AND t.category.type = 'INCOME'")
    BigDecimal totalIncome(Long companyId);

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.company.id = :companyId AND t.category.type = 'EXPENSE'")
    BigDecimal totalExpense(Long companyId);
}