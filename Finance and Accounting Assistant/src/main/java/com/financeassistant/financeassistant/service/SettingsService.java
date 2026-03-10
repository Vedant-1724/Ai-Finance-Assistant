package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.dto.UserSettingsDto;
import com.financeassistant.financeassistant.entity.Company;
import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.entity.UserEmailPrefs;
import com.financeassistant.financeassistant.repository.CompanyRepository;
import com.financeassistant.financeassistant.repository.UserEmailPrefsRepository;
import com.financeassistant.financeassistant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("INR", "USD", "EUR", "GBP", "AED", "SGD");

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final UserEmailPrefsRepository userEmailPrefsRepository;

    @Transactional(readOnly = true)
    public UserSettingsDto getSettings(User user) {
        Company company = findCompanyForUser(user.getId());
        UserEmailPrefs prefs = userEmailPrefsRepository.findByUserId(user.getId())
                .orElseGet(() -> buildDefaultPrefs(user.getId()));
        return toDto(user, company, prefs);
    }

    @Transactional
    public UserSettingsDto updateSettings(User user, UserSettingsDto request) {
        Company company = findCompanyForUser(user.getId());
        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));

        String normalizedEmail = normalizeEmail(request.getEmail(), managedUser.getEmail());
        if (!managedUser.getEmail().equalsIgnoreCase(normalizedEmail)
                && userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(CONFLICT, "That email is already in use.");
        }

        managedUser.setEmail(normalizedEmail);
        userRepository.save(managedUser);

        if (request.getCompanyName() != null && !request.getCompanyName().isBlank()) {
            company.setName(request.getCompanyName().trim());
        }
        company.setCurrency(normalizeCurrency(request.getCurrency(), company.getCurrency()));
        companyRepository.save(company);

        UserEmailPrefs prefs = userEmailPrefsRepository.findByUserId(user.getId())
                .orElseGet(() -> buildDefaultPrefs(user.getId()));
        applyPrefs(prefs, request.getEmailPrefs());
        userEmailPrefsRepository.save(prefs);

        return toDto(managedUser, company, prefs);
    }

    private Company findCompanyForUser(Long userId) {
        return companyRepository.findFirstByOwnerId(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Company not found"));
    }

    private UserEmailPrefs buildDefaultPrefs(Long userId) {
        UserEmailPrefs prefs = new UserEmailPrefs();
        prefs.setUserId(userId);
        return prefs;
    }

    private void applyPrefs(UserEmailPrefs prefs, UserSettingsDto.EmailPrefsDto emailPrefs) {
        if (emailPrefs == null) {
            return;
        }
        prefs.setAnomalyAlerts(emailPrefs.isAnomalyAlerts());
        prefs.setForecastAlerts(emailPrefs.isForecastAlerts());
        prefs.setBudgetAlerts(emailPrefs.isBudgetAlerts());
        prefs.setWeeklySummary(emailPrefs.isWeeklySummary());
        prefs.setTrialReminders(emailPrefs.isTrialReminders());
    }

    private UserSettingsDto toDto(User user, Company company, UserEmailPrefs prefs) {
        return new UserSettingsDto(
                user.getEmail(),
                company.getName(),
                company.getCurrency(),
                new UserSettingsDto.EmailPrefsDto(
                        prefs.isAnomalyAlerts(),
                        prefs.isForecastAlerts(),
                        prefs.isBudgetAlerts(),
                        prefs.isWeeklySummary(),
                        prefs.isTrialReminders()));
    }

    private String normalizeEmail(String requestedEmail, String currentEmail) {
        if (requestedEmail == null || requestedEmail.isBlank()) {
            return currentEmail;
        }
        return requestedEmail.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeCurrency(String requestedCurrency, String currentCurrency) {
        if (requestedCurrency == null || requestedCurrency.isBlank()) {
            return currentCurrency;
        }
        String normalized = requestedCurrency.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED_CURRENCIES.contains(normalized) ? normalized : currentCurrency;
    }
}
