package com.financeassistant.financeassistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

/**
 * Core application beans:
 *  - PasswordEncoder (BCrypt strength 12) — used by AuthService
 *  - RestTemplate — used by AiController to proxy requests to Python Flask
 */
@Configuration
public class AppConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}