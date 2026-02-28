package com.financeassistant.financeassistant.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory rate limiter using Bucket4j token bucket algorithm.
 *
 * Login:    5 attempts per minute per IP
 * Register: 3 attempts per 10 minutes per IP
 *
 * Protects against:
 *  - Brute-force password attacks
 *  - Credential stuffing attacks
 *  - Account enumeration attacks
 *
 * Critical before Razorpay integration — payment endpoints must not be
 * accessible to automated attackers who might have stolen credentials.
 */
@Component
public class LoginRateLimiter {

    @Value("${security.rate-limit.login-max-attempts:5}")
    private int loginMaxAttempts;

    @Value("${security.rate-limit.login-window-minutes:1}")
    private int loginWindowMinutes;

    @Value("${security.rate-limit.register-max-attempts:3}")
    private int registerMaxAttempts;

    @Value("${security.rate-limit.register-window-minutes:10}")
    private int registerWindowMinutes;

    // Separate buckets for login vs register
    private final Map<String, Bucket> loginBuckets    = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBuckets = new ConcurrentHashMap<>();

    private Bucket createLoginBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(
                        loginMaxAttempts,
                        Refill.intervally(loginMaxAttempts, Duration.ofMinutes(loginWindowMinutes))
                ))
                .build();
    }

    private Bucket createRegisterBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(
                        registerMaxAttempts,
                        Refill.intervally(registerMaxAttempts, Duration.ofMinutes(registerWindowMinutes))
                ))
                .build();
    }

    /**
     * Try to consume a login attempt for this IP.
     * Returns false if rate limit exceeded.
     */
    public boolean tryConsumeLogin(String ip) {
        return loginBuckets
                .computeIfAbsent(ip, k -> createLoginBucket())
                .tryConsume(1);
    }

    /**
     * Try to consume a register attempt for this IP.
     * Returns false if rate limit exceeded.
     */
    public boolean tryConsumeRegister(String ip) {
        return registerBuckets
                .computeIfAbsent(ip, k -> createRegisterBucket())
                .tryConsume(1);
    }
}