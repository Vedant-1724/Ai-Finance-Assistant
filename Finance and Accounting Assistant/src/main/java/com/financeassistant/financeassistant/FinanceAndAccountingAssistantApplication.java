package com.financeassistant.financeassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication(
        exclude = {
                // Prevents Spring Security from generating a random password
                // and blocking all endpoints with HTTP Basic Auth.
                // Remove this exclusion once you implement JWT authentication.
                UserDetailsServiceAutoConfiguration.class
        }
)
@EnableCaching
public class FinanceAndAccountingAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceAndAccountingAssistantApplication.class, args);
    }
}