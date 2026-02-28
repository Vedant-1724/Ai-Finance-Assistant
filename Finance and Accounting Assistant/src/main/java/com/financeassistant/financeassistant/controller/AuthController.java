package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.config.GlobalExceptionHandler.RateLimitException;
import com.financeassistant.financeassistant.dto.AuthResponse;
import com.financeassistant.financeassistant.dto.LoginRequest;
import com.financeassistant.financeassistant.dto.RegisterRequest;
import com.financeassistant.financeassistant.security.JwtUtil;
import com.financeassistant.financeassistant.security.LoginRateLimiter;
import com.financeassistant.financeassistant.security.TokenBlacklistService;
import com.financeassistant.financeassistant.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication controller — hardened for production.
 *
 * Security features:
 *  - Rate limiting: 5 logins/min, 3 registers/10min per IP
 *  - Logout blacklists JWT in Redis
 *  - Never reveals whether email exists (prevents enumeration)
 *  - Input validated via @Valid annotations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService           authService;
    private final LoginRateLimiter      rateLimiter;
    private final TokenBlacklistService blacklistService;
    private final JwtUtil               jwtUtil;

    /**
     * POST /api/v1/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest httpReq) {

        String ip = getClientIp(httpReq);

        // Rate limit: 5 attempts per minute per IP
        if (!rateLimiter.tryConsumeLogin(ip)) {
            log.warn("Rate limit exceeded for login from IP: {}", ip);
            throw new RateLimitException("Too many login attempts. Please wait 1 minute.");
        }

        try {
            AuthResponse response = authService.login(req);
            log.info("Login success: {} from IP: {}", req.getEmail(), ip);
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException e) {
            // Generic message — don't reveal if email exists
            log.warn("Login failed for {} from IP: {}", req.getEmail(), ip);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid email or password"));
        } catch (Exception e) {
            log.error("Login error for {}: {}", req.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid email or password"));
        }
    }

    /**
     * POST /api/v1/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest req,
            HttpServletRequest httpReq) {

        String ip = getClientIp(httpReq);

        // Rate limit: 3 registrations per 10 minutes per IP
        if (!rateLimiter.tryConsumeRegister(ip)) {
            log.warn("Rate limit exceeded for register from IP: {}", ip);
            throw new RateLimitException("Too many registration attempts. Please wait 10 minutes.");
        }

        try {
            AuthResponse response = authService.register(req);
            log.info("Registration success: {} from IP: {}", req.getEmail(), ip);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Register error for {}: {}", req.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Registration failed. Please try again."));
        }
    }

    /**
     * POST /api/v1/auth/logout
     * Blacklists the current JWT token — user is immediately logged out
     * even before the token naturally expires.
     *
     * Critical for: subscription cancellation, account compromise response.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest httpReq) {
        String authHeader = httpReq.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            long expiryMs = jwtUtil.getExpiryMs(token) - System.currentTimeMillis();

            if (expiryMs > 0) {
                blacklistService.blacklist(token, expiryMs);
            }

            log.info("User logged out from IP: {}", getClientIp(httpReq));
        }

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /**
     * GET /api/v1/auth/me
     * Returns current user info from JWT without hitting the database.
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest httpReq) {
        String authHeader = httpReq.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Not authenticated"));
        }

        String token = authHeader.substring(7);
        String email     = jwtUtil.extractEmail(token);
        Long   companyId = jwtUtil.extractCompanyId(token);

        return ResponseEntity.ok(Map.of(
            "email",     email != null ? email : "",
            "companyId", companyId != null ? companyId : 0
        ));
    }

    // ── Helper: get real IP (works behind nginx reverse proxy) ───────────────
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}