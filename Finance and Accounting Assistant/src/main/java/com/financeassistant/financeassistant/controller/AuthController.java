package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.dto.AuthResponse;
import com.financeassistant.financeassistant.dto.LoginRequest;
import com.financeassistant.financeassistant.dto.RegisterRequest;
import com.financeassistant.financeassistant.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication endpoints — these are PUBLIC (no JWT required).
 * Configured as open in CorsConfig under /api/v1/auth/**.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/v1/auth/login
     * Body: { "email": "admin@finance.com", "password": "password123" }
     * Returns: { "token": "...", "companyId": 1, "email": "admin@finance.com" }
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        log.info("POST /auth/login email={}", req.getEmail());
        try {
            AuthResponse response = authService.login(req);
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        } catch (Exception e) {
            log.error("Login failed for {}: {}", req.getEmail(), e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        }
    }

    /**
     * POST /api/v1/auth/register
     * Body: { "email": "user@example.com", "password": "secret", "companyName": "Acme Ltd" }
     * Returns: { "token": "...", "companyId": 2, "email": "user@example.com" }
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        log.info("POST /auth/register email={}", req.getEmail());
        try {
            AuthResponse response = authService.register(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Register failed for {}: {}", req.getEmail(), e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Registration failed"));
        }
    }
}
