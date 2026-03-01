package com.financeassistant.financeassistant.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory rate limiter using Bucket4j (updated to non-deprecated API).
 *
 * Login:    5 attempts per minute per IP
 * Register: 3 attempts per 10 minutes per IP
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

    private final Map<String, Bucket> loginBuckets    = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBuckets = new ConcurrentHashMap<>();

    private Bucket createLoginBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(loginMaxAttempts)
                .refillGreedy(loginMaxAttempts, Duration.ofMinutes(loginWindowMinutes))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createRegisterBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(registerMaxAttempts)
                .refillGreedy(registerMaxAttempts, Duration.ofMinutes(registerWindowMinutes))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    public boolean tryConsumeLogin(String ip) {
        return loginBuckets.computeIfAbsent(ip, k -> createLoginBucket()).tryConsume(1);
    }

    public boolean tryConsumeRegister(String ip) {
        return registerBuckets.computeIfAbsent(ip, k -> createRegisterBucket()).tryConsume(1);
    }
}