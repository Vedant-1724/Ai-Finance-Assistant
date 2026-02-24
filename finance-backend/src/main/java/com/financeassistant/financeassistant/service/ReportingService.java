package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.dto.PnLReport;
import com.financeassistant.financeassistant.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class ReportingService {

    @Autowired
    private TransactionRepository repo;

    @Cacheable(value = "pnl-report",
            key = "#companyId + ':' + #month",
            unless = "#result == null")
    public PnLReport getPnL(Long companyId, String month) {
        BigDecimal income  = repo.totalIncome(companyId);
        BigDecimal expense = repo.totalExpense(companyId);
        return new PnLReport(income, expense);
    }

    @CacheEvict(value = "pnl-report", allEntries = true)
    public void invalidateReports(Long companyId) {}
}