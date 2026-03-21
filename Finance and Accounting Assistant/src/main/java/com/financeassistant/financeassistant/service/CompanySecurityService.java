package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.entity.User;
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

    private final WorkspaceAccessService workspaceAccessService;

    /**
     * Returns true only if the authenticated user owns the given company.
     * Used in @PreAuthorize annotations throughout controllers.
     */
    public boolean isOwner(Long companyId, Authentication authentication) {
        return isCompanyOwner(companyId, authentication);
    }

    public boolean isCompanyOwner(Long companyId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        try {
            User user = (User) authentication.getPrincipal();
            boolean owns = workspaceAccessService.isCompanyOwner(companyId, user);

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

    public boolean isCompanyMember(Long companyId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        try {
            User user = (User) authentication.getPrincipal();
            return workspaceAccessService.isCompanyMember(companyId, user);
        } catch (Exception e) {
            log.error("Company membership check failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean canEditFinance(Long companyId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        try {
            User user = (User) authentication.getPrincipal();
            return workspaceAccessService.canEditFinance(companyId, user);
        } catch (Exception e) {
            log.error("Finance edit permission check failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean isCurrentUserOwner(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        try {
            User user = (User) authentication.getPrincipal();
            return workspaceAccessService.isWorkspaceOwner(user);
        } catch (Exception e) {
            log.error("Workspace owner check failed: {}", e.getMessage());
            return false;
        }
    }
}
