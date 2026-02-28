package com.financeassistant.financeassistant.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT token creation and validation.
 *
 * Security hardening:
 *  - HS256 with a Base64-encoded secret (min 32 bytes)
 *  - Claims include: email, companyId, issued-at, expiry
 *  - Token expiry configurable via environment variable
 *  - All exceptions caught and logged — never leaks internals
 *
 * Ready for Razorpay: companyId in claims lets payment controller
 * verify the user's company without a database call.
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private long expirationMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate a signed JWT containing email and companyId.
     */
    public String generateToken(String email, Long companyId) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
            .subject(email)
            .claim("companyId", companyId)
            .claim("type", "access")
            .issuedAt(now)
            .expiration(expiry)
            .signWith(getSigningKey())
            .compact();
    }

    /**
     * Extract the email (subject) from a token.
     * Returns null if token is invalid/expired.
     */
    public String extractEmail(String token) {
        try {
            return getClaims(token).getSubject();
        } catch (Exception e) {
            log.debug("Could not extract email from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract companyId from token claims.
     */
    public Long extractCompanyId(String token) {
        try {
            Object id = getClaims(token).get("companyId");
            if (id instanceof Integer) return ((Integer) id).longValue();
            if (id instanceof Long)    return (Long) id;
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get token expiry as milliseconds from epoch.
     */
    public long getExpiryMs(String token) {
        try {
            return getClaims(token).getExpiration().getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Validate token: signature, expiry, and email match.
     */
    public boolean isTokenValid(String token, String email) {
        try {
            String tokenEmail = extractEmail(token);
            return email.equals(tokenEmail) && !isExpired(token);
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean isExpired(String token) {
        try {
            return getClaims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}