package com.financeassistant.financeassistant.service;

// PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/financeassistant/service/ScheduledTaskService.java

import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.repository.CompanyRepository;
import com.financeassistant.financeassistant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ScheduledTaskService
 * Runs nightly background jobs:
 *   1. Trial expiry reminders (Day 4 and Day 5 of trial)
 *   2. Budget threshold alerts
 *   3. Monthly health score emails (1st of each month)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskService {

    private final UserRepository userRepo;
    private final CompanyRepository companyRepo;
    private final EmailAlertService emailAlertService;
    private final BudgetService budgetService;
    private final FinancialHealthService healthService;

    /** Runs daily at 09:00 to send trial expiry reminders */
    @Scheduled(cron = "0 0 9 * * *")
    public void sendTrialExpiryReminders() {
        log.info("Running trial expiry reminder job");
        List<User> users = userRepo.findAll();
        for (User u : users) {
            long daysLeft = u.trialDaysRemaining();
            if (daysLeft == 1 || daysLeft == 2) {
                emailAlertService.sendTrialExpiryReminder(u, daysLeft);
            }
        }
    }

    /** Runs on the 1st of every month at 08:00 to send health score emails */
    @Scheduled(cron = "0 0 8 1 * *")
    public void sendMonthlyHealthScores() {
        log.info("Running monthly health score email job");
        // For each company, compute score and email the owner
        companyRepo.findAll().forEach(company -> {
            try {
                userRepo.findById(company.getOwnerId()).ifPresent(owner -> {
                    if (owner.hasPremiumAccess()) {
                        var score = healthService.getOrComputeScore(company.getId(),
                                java.time.LocalDate.now().minusMonths(1));
                        String month = java.time.LocalDate.now().minusMonths(1)
                                .format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"));
                        emailAlertService.sendMonthlyHealthScore(owner, score.getScore(),
                                month, score.getRecommendations());
                    }
                });
            } catch (Exception e) {
                log.error("Health score email failed for company {}: {}", company.getId(), e.getMessage());
            }
        });
    }
}
