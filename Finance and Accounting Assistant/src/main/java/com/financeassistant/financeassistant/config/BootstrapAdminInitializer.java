package com.financeassistant.financeassistant.config;

import com.financeassistant.financeassistant.entity.Company;
import com.financeassistant.financeassistant.entity.CompanyMember;
import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.entity.User.SubscriptionStatus;
import com.financeassistant.financeassistant.entity.UserEmailPrefs;
import com.financeassistant.financeassistant.repository.CompanyMemberRepository;
import com.financeassistant.financeassistant.repository.CompanyRepository;
import com.financeassistant.financeassistant.repository.UserEmailPrefsRepository;
import com.financeassistant.financeassistant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final UserEmailPrefsRepository userEmailPrefsRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin.enabled:true}")
    private boolean bootstrapEnabled;

    @Value("${app.bootstrap.admin.email:vedantj553@gmail.com}")
    private String adminEmail;

    @Value("${app.bootstrap.admin.password:Vedant6927}")
    private String adminPassword;

    @Value("${app.bootstrap.admin.company-name:Joshi}")
    private String companyName;

    @Value("${app.bootstrap.admin.plan:MAX}")
    private String subscriptionPlan;

    @Value("${app.bootstrap.admin.currency:INR}")
    private String companyCurrency;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!bootstrapEnabled) {
            log.info("Bootstrap admin initializer is disabled.");
            return;
        }

        String normalizedEmail = adminEmail.trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail).orElseGet(User::new);
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(adminPassword));
        user.setRole("ADMIN");
        user.setEmailVerified(true);
        user.setTrialStartedAt(null);
        user.setSubscriptionStatus(resolvePlan(subscriptionPlan));
        user.setSubscriptionExpiresAt(null);
        user.setRazorpaySubscriptionId("bootstrap-admin");
        user.setAiChatsUsedToday(0);
        user.setAiChatResetDate(LocalDate.now());
        user = userRepository.save(user);

        Company company = companyRepository.findFirstByOwnerId(user.getId()).orElseGet(Company::new);
        company.setOwnerId(user.getId());
        company.setName(companyName);
        company.setCurrency(companyCurrency);
        company = companyRepository.save(company);

        CompanyMember membership = companyMemberRepository.findByCompanyIdAndUserId(company.getId(), user.getId())
                .orElseGet(CompanyMember::new);
        membership.setCompanyId(company.getId());
        membership.setUserId(user.getId());
        membership.setRole(CompanyMember.Role.OWNER);
        membership.setInviteEmail(null);
        membership.setInviteToken(null);
        membership.setInviteExpiresAt(null);
        membership.setAcceptedAt(LocalDateTime.now());
        companyMemberRepository.save(membership);

        UserEmailPrefs prefs = userEmailPrefsRepository.findByUserId(user.getId()).orElseGet(UserEmailPrefs::new);
        prefs.setUserId(user.getId());
        prefs.setAnomalyAlerts(true);
        prefs.setForecastAlerts(true);
        prefs.setBudgetAlerts(true);
        prefs.setWeeklySummary(true);
        prefs.setTrialReminders(true);
        userEmailPrefsRepository.save(prefs);

        log.info("Bootstrapped admin user {} with company {} and plan {}.", normalizedEmail, company.getName(),
                user.getEffectiveTier());
    }

    private SubscriptionStatus resolvePlan(String plan) {
        if (plan == null) {
            return SubscriptionStatus.MAX;
        }

        return switch (plan.trim().toUpperCase()) {
            case "ACTIVE", "PRO" -> SubscriptionStatus.ACTIVE;
            case "TRIAL" -> SubscriptionStatus.TRIAL;
            case "FREE" -> SubscriptionStatus.FREE;
            case "CANCELLED" -> SubscriptionStatus.CANCELLED;
            case "EXPIRED" -> SubscriptionStatus.EXPIRED;
            case "MAX" -> SubscriptionStatus.MAX;
            default -> SubscriptionStatus.MAX;
        };
    }
}
