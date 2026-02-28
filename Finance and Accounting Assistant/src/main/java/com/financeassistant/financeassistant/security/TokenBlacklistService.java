package com.financeassistant.financeassistant.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Blacklists JWT tokens on logout using Redis.
 * Token stays blacklisted until its natural expiry time.
 *
 * Used by: AuthController logout endpoint.
 * Checked by: JwtAuthFilter on every request.
 *
 * Critical for: Razorpay — when a user's subscription is cancelled,
 * we can immediately invalidate their token.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    /**
     * Blacklist a token for the given duration (match its remaining TTL).
     */
    public void blacklist(String token, long expiryMillis) {
        try {
            String key = BLACKLIST_PREFIX + token;
            redisTemplate.opsForValue().set(key, "revoked", Duration.ofMillis(expiryMillis));
            log.info("Token blacklisted, expires in {}ms", expiryMillis);
        } catch (Exception e) {
            // Redis failure should not block logout
            log.error("Failed to blacklist token in Redis: {}", e.getMessage());
        }
    }

    /**
     * Check if a token has been blacklisted (logged out).
     */
    public boolean isBlacklisted(String token) {
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.hasKey(BLACKLIST_PREFIX + token)
            );
        } catch (Exception e) {
            // Redis failure — be safe, treat as NOT blacklisted
            log.error("Redis blacklist check failed: {}", e.getMessage());
            return false;
        }
    }
}