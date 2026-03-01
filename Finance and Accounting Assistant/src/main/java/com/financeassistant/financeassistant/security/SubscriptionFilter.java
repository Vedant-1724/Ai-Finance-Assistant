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
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * PATH: finance-backend/src/main/java/com/financeassistant/financeassistant/security/SubscriptionFilter.java
 *
 * Runs AFTER JwtAuthFilter. Blocks authenticated users whose trial has
 * expired or whose subscription has lapsed from calling any business API.
 *
 * Exemptions (always allowed, no subscription check):
 *  - /api/v1/auth/**         — login, register, logout
 *  - /api/v1/payment/**      — Razorpay order creation + webhook + status
 *  - /actuator/health        — health probe
 *
 * Returns 402 Payment Required JSON so the React frontend can redirect
 * the user to the upgrade/subscription page.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionFilter extends OncePerRequestFilter {

    private static final Set<String> EXEMPT_PREFIXES = Set.of(
            "/api/v1/auth/",
            "/api/v1/payment/",
            "/actuator/"
    );

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         chain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip subscription check for public/payment endpoints
        if (isExempt(path)) {
            chain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // If not authenticated yet, let Spring Security handle the 401
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof User user)) {
            chain.doFilter(request, response);
            return;
        }

        // Gate: trial / subscription must be active
        if (!user.isSubscriptionActive()) {
            log.warn("Blocked subscription-expired user {} accessing {}", user.getEmail(), path);
            response.setStatus(HttpServletResponse.SC_PAYMENT_REQUIRED); // 402
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                objectMapper.writeValueAsString(Map.of(
                    "error",   "SUBSCRIPTION_EXPIRED",
                    "message", "Your free trial has ended. Please subscribe to continue.",
                    "upgradeUrl", "/subscription"
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
}
