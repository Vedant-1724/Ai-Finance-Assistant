package com.financeassistant.financeassistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Core application beans.
 * BCrypt strength raised to 12 for production-grade password hashing.
 * Strength 12 = ~300ms per hash — strong against brute-force.
 * Razorpay payments and bank linking require this level of security.
 */
@Configuration
public class AppConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 12 rounds = production standard (OWASP recommendation)
        // 10 = dev default, 12 = production, 14 = high-security
        return new BCryptPasswordEncoder(12);
    }
}