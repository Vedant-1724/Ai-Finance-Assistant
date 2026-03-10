package com.financeassistant.financeassistant.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms:86400000}")
    private long expirationMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String email, Long companyId) {
        Date now = new Date();
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

    public String extractEmail(String token) {
        try {
            return getClaims(token).getSubject();
        } catch (Exception e) {
            log.debug("Could not extract email from token: {}", e.getMessage());
            return null;
        }
    }

    public Long extractCompanyId(String token) {
        try {
            Object id = getClaims(token).get("companyId");
            if (id instanceof Integer integerId) {
                return integerId.longValue();
            }
            if (id instanceof Long longId) {
                return longId;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public long getExpiryMs(String token) {
        try {
            return getClaims(token).getExpiration().getTime();
        } catch (Exception e) {
            return 0;
        }
    }

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
