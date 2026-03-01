package com.financeassistant.financeassistant.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financeassistant.financeassistant.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * PATH: finance-backend/src/main/java/com/financeassistant/financeassistant/security/SubscriptionFilter.java
 *
 * CHANGES:
 *  - FREE/EXPIRED/CANCELLED users are no longer hard-blocked from all APIs.
 *    They get through, but premium endpoints return 402 FEATURE_LOCKED.
 *  - Premium endpoints (forecast, anomaly, OCR, P&L) are blocked for FREE tier.
 *  - Sets X-Subscription-Tier header on every request for downstream use.
 *  - TRIAL users pass through to all premium endpoints (within 5-day window).
 *
 * Tier access matrix:
 *   FREE/EXPIRED/CANCELLED → blocked on PREMIUM_ENDPOINTS set, allowed on rest
 *   TRIAL (active)         → all endpoints allowed
 *   ACTIVE (not expired)   → all endpoints allowed
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionFilter extends OncePerRequestFilter {

    // Always exempted — no subscription check at all
    private static final Set<String> EXEMPT_PREFIXES = Set.of(
            "/api/v1/auth/",
            "/api/v1/payment/",
            "/api/v1/subscription/start-trial",
            "/actuator/"
    );

    // These endpoints require premium access (TRIAL or ACTIVE)
    private static final Set<String> PREMIUM_ENDPOINT_FRAGMENTS = Set.of(
            "/ai/forecast",
            "/ai/anomaly",
            "/ai/ocr",
            "/reports/pnl",
            "/reports/"
    );

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         chain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip all checks for public endpoints
        if (isExempt(path)) {
            chain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Not authenticated — let Spring Security handle 401
        if (auth == null || !auth.isAuthenticated() ||
                !(auth.getPrincipal() instanceof User user)) {
            chain.doFilter(request, response);
            return;
        }

        String effectiveTier = user.getEffectiveTier();

        // Always inject tier header so controllers can use it
        response.setHeader("X-Subscription-Tier", effectiveTier);

        // FREE/EXPIRED/CANCELLED: block premium endpoints
        if ("FREE".equals(effectiveTier) && isPremiumEndpoint(path)) {
            log.info("Blocked free-tier user {} from premium endpoint {}", user.getEmail(), path);
            response.setStatus(HttpServletResponse.SC_PAYMENT_REQUIRED); // 402
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                objectMapper.writeValueAsString(Map.of(
                    "error",       "FEATURE_LOCKED",
                    "message",     "This feature requires a Premium or Trial subscription.",
                    "tier",        effectiveTier,
                    "upgradeUrl",  "/subscription"
                ))
            );
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isExempt(String path) {
        for (String prefix : EXEMPT_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return path.equals("/actuator/health");
    }

    private boolean isPremiumEndpoint(String path) {
        for (String fragment : PREMIUM_ENDPOINT_FRAGMENTS) {
            if (path.contains(fragment)) return true;
        }
        return false;
    }
}