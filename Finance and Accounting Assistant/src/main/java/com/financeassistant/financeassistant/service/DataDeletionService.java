package com.financeassistant.financeassistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for wiping out a user's data from the system entirely.
 * Used for compliance (Data Deletion/GDPR/DPDP Act).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataDeletionService {

  private final JdbcTemplate jdbcTemplate;

  /**
   * Executes a cascade deletion of all user and company related data.
   * Uses JdbcTemplate for performance and to bypass JPA N+1 delete issues.
   */
  @Transactional
  public void deleteUserData(Long userId, Long companyId) {
    log.warn("Initiating complete data wipe for user: {} and company: {}", userId, companyId);

    if (companyId != null) {
      log.info("Deleting company-associated records for company: {}", companyId);
      // 1. Delete associated leaf records first
      jdbcTemplate.update(
          "DELETE FROM anomalies WHERE transaction_id IN (SELECT id FROM transactions WHERE company_id = ?)",
          companyId);
      jdbcTemplate.update("DELETE FROM audit_logs WHERE company_id = ?", companyId);
      jdbcTemplate.update("DELETE FROM budgets WHERE company_id = ?", companyId);
      jdbcTemplate.update("DELETE FROM company_members WHERE company_id = ?", companyId);
      jdbcTemplate.update("DELETE FROM financial_health_scores WHERE company_id = ?", companyId);
      jdbcTemplate.update("DELETE FROM recurring_transactions WHERE company_id = ?", companyId);
      // 2. Delete transactions & categories
      jdbcTemplate.update("DELETE FROM transactions WHERE company_id = ?", companyId);
      jdbcTemplate.update("DELETE FROM categories WHERE company_id = ?", companyId);
      // 3. Delete accounts if present
      try {
        jdbcTemplate.update("DELETE FROM accounts WHERE company_id = ?", companyId);
      } catch (Exception e) {
        log.debug("No accounts table found to wipe.");
      }
      // 4. Finally, delete the company itself
      jdbcTemplate.update("DELETE FROM companies WHERE id = ?", companyId);
    }

    // 5. Delete user-associated records
    log.info("Deleting user records for user: {}", userId);
    jdbcTemplate.update("DELETE FROM user_email_prefs WHERE user_id = ?", userId);
    jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);

    log.info("Successfully wiped all data for user: {}", userId);
  }
}
