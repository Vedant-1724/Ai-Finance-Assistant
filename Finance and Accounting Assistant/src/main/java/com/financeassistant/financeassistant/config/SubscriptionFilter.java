package com.financeassistant.financeassistant.security;

// PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/financeassistant/security/SubscriptionFilter.java
// UPDATED: Added new premium endpoint fragments for new features

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financeassistant.financeassistant.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    // Paths that never hit the subscription gate
    private static final List<String> EXEMPT_PREFIXES = List.of(
            "/api/v1/auth/",
            "/api/v1/payment/",
            "/api/v1/subscription/",
            "/actuator/",
            "/api/v1/"    // Base path prefix — actual gating is by fragment below
    );

    // URL fragments that require TRIAL or ACTIVE subscription
    private static final List<String> PREMIUM_ENDPOINT_FRAGMENTS = List.of(
            "/ai/forecast",
            "/ai/anomalies",
            "/ocr",
            "/invoices",
            "/reports/pnl",
            "/reports/summary",
            "/export/pdf",          // PDF export = premium
            "/charts",              // Live charts = premium
            "/health/",             // Health score = premium
            "/tax/",                // Tax estimation = premium
            "/recurring/",          // Recurring transactions = premium
            "/audit",               // Audit log = premium
            "/team/"                // Team management = premium
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip unauthenticated or exempt paths
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof User user)) {
            chain.doFilter(request, response);
            return;
        }

        String effectiveTier = user.getEffectiveTier();
        response.setHeader("X-Subscription-Tier", effectiveTier);

        // FREE/EXPIRED/CANCELLED: block premium endpoints
        if ("FREE".equals(effectiveTier) && isPremiumEndpoint(path)) {
            log.info("Blocked free-tier user {} from premium endpoint {}", user.getEmail(), path);
            response.setStatus(HttpServletResponse.SC_PAYMENT_REQUIRED); // 402
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "error",      "FEATURE_LOCKED",
                    "message",    "This feature requires a Premium or Trial subscription.",
                    "tier",       effectiveTier,
                    "upgradeUrl", "/subscription"
            )));
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isPremiumEndpoint(String path) {
        for (String fragment : PREMIUM_ENDPOINT_FRAGMENTS) {
            if (path.contains(fragment)) return true;
        }
        return false;
    }
}
