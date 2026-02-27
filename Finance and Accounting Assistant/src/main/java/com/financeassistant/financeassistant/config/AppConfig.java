package com.financeassistant.financeassistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Application-level beans.
 *
 * AuthenticationManager has been REMOVED from this class.
 *
 * It was the source of the second circular dependency:
 *   AuthService → AuthenticationManager
 *       → (Spring Security scans for UserDetailsService)
 *       → AuthService   ← cycle!
 *
 * Since AuthService now verifies passwords directly with PasswordEncoder,
 * AuthenticationManager is no longer needed anywhere in the codebase.
 */
@Configuration
public class AppConfig {

    /**
     * BCrypt password encoder with cost factor 10.
     * Used by AuthService to hash new passwords and verify login passwords.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}