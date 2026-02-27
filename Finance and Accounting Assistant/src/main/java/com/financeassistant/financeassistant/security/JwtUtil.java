package com.financeassistant.financeassistant.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT utility using JJWT 0.12.x API.
 *
 * Tokens contain two claims:
 *   sub        → user's email (standard JWT subject)
 *   companyId  → the user's primary company ID
 *
 * The secret key is read from application.yaml (jwt.secret) and must be a
 * Base64-encoded string that decodes to at least 32 bytes (256 bits) for HS256.
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long expiration;   // milliseconds

    // ── Token Generation ──────────────────────────────────────────────────────

    /**
     * Builds a signed HS256 JWT containing the user's email as subject
     * and companyId as a custom claim.
     */
    public String generateToken(String email, Long companyId) {
        return Jwts.builder()
                .subject(email)
                .claim("companyId", companyId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    // ── Token Parsing ─────────────────────────────────────────────────────────

    /** Extracts the email (subject) from the token. Returns null if invalid. */
    public String extractEmail(String token) {
        try {
            return parseClaims(token).getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Could not extract email from token: {}", e.getMessage());
            return null;
        }
    }

    /** Extracts the companyId custom claim. Returns null if invalid. */
    public Long extractCompanyId(String token) {
        try {
            Object raw = parseClaims(token).get("companyId");
            if (raw == null) return null;
            return Long.valueOf(raw.toString());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Could not extract companyId from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Returns true if the token is valid (signature OK, not expired)
     * and the subject matches the provided email.
     */
    public boolean isTokenValid(String token, String email) {
        try {
            String subject = parseClaims(token).getSubject();
            return email.equals(subject);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Parses and verifies the token, returning the Claims payload.
     * Throws JwtException on invalid signature or expired token.
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Decodes the Base64 secret from application.yaml and builds an
     * HMAC-SHA256 SecretKey suitable for signing and verifying JWTs.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
