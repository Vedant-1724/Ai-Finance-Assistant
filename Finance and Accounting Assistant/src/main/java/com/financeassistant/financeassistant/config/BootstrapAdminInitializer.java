package com.financeassistant.financeassistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final UserEmailPrefsRepository userEmailPrefsRepository;
    private final PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.bootstrap.admin.enabled:true}")
    private boolean bootstrapEnabled;

    @Value("${app.bootstrap.admin.file:}")
    private String adminConfigFile;

    @Value("${app.bootstrap.admin.email:}")
    private String adminEmail;

    @Value("${app.bootstrap.admin.password:}")
    private String adminPassword;

    @Value("${app.bootstrap.admin.company-name:}")
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

        BootstrapAdminConfig config = resolveAdminConfig();
        if (config == null) {
            log.warn("Bootstrap admin config not found. Set app.bootstrap.admin.* properties or provide admin.local.json for local development.");
            return;
        }

        String normalizedEmail = config.email().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail).orElseGet(User::new);
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(config.password()));
        user.setRole("ADMIN");
        user.setEmailVerified(true);
        user.setTrialStartedAt(null);
        user.setSubscriptionStatus(resolvePlan(config.plan()));
        user.setSubscriptionExpiresAt(null);
        user.setRazorpaySubscriptionId("bootstrap-admin");
        user.setAiChatsUsedToday(0);
        user.setAiChatResetDate(LocalDate.now());
        user = userRepository.save(user);

        Company company = companyRepository.findFirstByOwnerId(user.getId()).orElseGet(Company::new);
        company.setOwnerId(user.getId());
        company.setName(config.companyName());
        company.setCurrency(config.currency());
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

    private BootstrapAdminConfig resolveAdminConfig() {
        BootstrapAdminConfig fileConfig = loadFromFile();
        if (isValid(fileConfig)) {
            return normalize(fileConfig);
        }

        BootstrapAdminConfig propertyConfig = new BootstrapAdminConfig(
                adminEmail,
                adminPassword,
                companyName,
                subscriptionPlan,
                companyCurrency
        );
        if (isValid(propertyConfig)) {
            return normalize(propertyConfig);
        }

        return null;
    }

    private BootstrapAdminConfig loadFromFile() {
        Set<Path> candidates = new LinkedHashSet<>();
        if (StringUtils.hasText(adminConfigFile)) {
            candidates.add(Path.of(adminConfigFile.trim()).toAbsolutePath().normalize());
        }

        Path workingDir = Path.of("").toAbsolutePath().normalize();
        candidates.add(workingDir.resolve("admin.local.json").normalize());
        if (workingDir.getParent() != null) {
            candidates.add(workingDir.getParent().resolve("admin.local.json").normalize());
        }

        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                BootstrapAdminConfig config = objectMapper.readValue(candidate.toFile(), BootstrapAdminConfig.class);
                if (isValid(config)) {
                    log.info("Loaded bootstrap admin config from {}", candidate);
                    return config;
                }
                log.warn("Bootstrap admin config file {} is missing required fields.", candidate);
            } catch (IOException exc) {
                log.warn("Failed to read bootstrap admin config from {}: {}", candidate, exc.getMessage());
            }
        }

        return null;
    }

    private BootstrapAdminConfig normalize(BootstrapAdminConfig config) {
        return new BootstrapAdminConfig(
                config.email() == null ? null : config.email().trim(),
                config.password() == null ? null : config.password().trim(),
                config.companyName() == null ? null : config.companyName().trim(),
                StringUtils.hasText(config.plan()) ? config.plan().trim() : subscriptionPlan,
                StringUtils.hasText(config.currency()) ? config.currency().trim().toUpperCase() : companyCurrency
        );
    }

    private boolean isValid(BootstrapAdminConfig config) {
        return config != null
                && StringUtils.hasText(config.email())
                && StringUtils.hasText(config.password())
                && StringUtils.hasText(config.companyName());
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

    private record BootstrapAdminConfig(
            String email,
            String password,
            String companyName,
            String plan,
            String currency
    ) {
    }
}

