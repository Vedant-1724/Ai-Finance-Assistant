package com.financeassistant.financeassistant.entity;

// PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/financeassistant/entity/UserEmailPrefs.java

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "user_email_prefs")
public class UserEmailPrefs {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "anomaly_alerts", nullable = false)
    private boolean anomalyAlerts = true;

    @Column(name = "forecast_alerts", nullable = false)
    private boolean forecastAlerts = true;

    @Column(name = "budget_alerts", nullable = false)
    private boolean budgetAlerts = true;

    @Column(name = "weekly_summary", nullable = false)
    private boolean weeklySummary = true;

    @Column(name = "trial_reminders", nullable = false)
    private boolean trialReminders = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
