package com.financeassistant.financeassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingsDto {

    private String email;
    private String companyName;
    private String currency;
    private EmailPrefsDto emailPrefs;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailPrefsDto {
        private boolean anomalyAlerts;
        private boolean forecastAlerts;
        private boolean budgetAlerts;
        private boolean weeklySummary;
        private boolean trialReminders;
    }
}
