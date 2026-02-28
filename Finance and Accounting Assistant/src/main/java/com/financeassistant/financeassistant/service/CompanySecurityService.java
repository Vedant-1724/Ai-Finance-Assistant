package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Security service used in @PreAuthorize expressions.
 *
 * FIXED: Was "return true" (open for development).
 * Now properly verifies that the authenticated user owns the company.
 *
 * This is CRITICAL for multi-tenant security:
 *  - User A must NOT access User B's transactions
 *  - User A must NOT access User B's P&L reports
 *  - User A must NOT trigger payments for User B's company
 *
 * Usage in controllers:
 *   @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
 */
@Slf4j
@Service("companySecurityService")
@RequiredArgsConstructor
public class CompanySecurityService {

    private final CompanyRepository companyRepository;

    /**
     * Returns true only if the authenticated user owns the given company.
     * Used in @PreAuthorize annotations throughout controllers.
     */
    public boolean isOwner(Long companyId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        try {
            User user = (User) authentication.getPrincipal();
            boolean owns = companyRepository.existsByIdAndOwnerId(companyId, user.getId());

            if (!owns) {
                log.warn("SECURITY: User {} attempted to access company {} — DENIED",
                         user.getEmail(), companyId);
            }

            return owns;
        } catch (Exception e) {
            log.error("Company ownership check failed: {}", e.getMessage());
            return false;
        }
    }
}