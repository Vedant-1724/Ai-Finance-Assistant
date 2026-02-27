package com.financeassistant.financeassistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Application-level beans that are intentionally separated from CorsConfig
 * to prevent a circular dependency:
 *
 *   CorsConfig → JwtAuthFilter → AuthService → PasswordEncoder
 *
 * By putting PasswordEncoder here, AuthService depends on AppConfig
 * (not on CorsConfig), breaking the cycle.
 */
@Configuration
public class AppConfig {

    /**
     * BCrypt password encoder — cost factor 10 (Spring default).
     * Used by AuthService.register() to hash passwords and
     * AuthService.login() via AuthenticationManager.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Exposes the Spring Security AuthenticationManager as a bean
     * so AuthService can call authenticate() for login.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
