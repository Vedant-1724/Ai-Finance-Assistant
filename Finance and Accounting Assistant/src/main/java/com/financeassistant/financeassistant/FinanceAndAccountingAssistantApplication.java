package com.financeassistant.financeassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Main application class.
 *
 * The UserDetailsServiceAutoConfiguration exclusion has been REMOVED because
 * we now have a real UserDetailsService (AuthService) that Spring Security
 * will auto-configure. Keeping the exclusion would break JWT authentication.
 */
@SpringBootApplication
@EnableCaching
public class FinanceAndAccountingAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceAndAccountingAssistantApplication.class, args);
    }
}
