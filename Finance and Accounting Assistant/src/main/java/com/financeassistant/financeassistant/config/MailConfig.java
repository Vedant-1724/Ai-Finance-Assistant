package com.financeassistant.financeassistant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * MailConfig
 *
 * Enables Spring's @Async support so EmailAlertService.sendAnomalyAlert()
 * runs on a background thread and never blocks the RabbitMQ consumer.
 *
 * JavaMailSender is auto-configured by Spring Boot when
 * spring.mail.host is present in application.yaml — no @Bean needed here.
 *
 * Place at:
 *   Finance and Accounting Assistant/src/main/java/com/financeassistant/
 *   financeassistant/config/MailConfig.java
 */
@EnableAsync
@Configuration
public class MailConfig {
    // Spring Boot auto-configures JavaMailSender from application.yaml
    // @EnableAsync activates @Async on EmailAlertService.sendAnomalyAlert()
}
