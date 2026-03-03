package com.financeassistant.financeassistant.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "notification_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "anomaly_alerts")
    @Builder.Default
    private boolean anomalyAlerts = true;

    @Column(name = "forecast_alerts")
    @Builder.Default
    private boolean forecastAlerts = true;

    @Column(name = "budget_alerts")
    @Builder.Default
    private boolean budgetAlerts = true;

    @Column(name = "trial_reminders")
    @Builder.Default
    private boolean trialReminders = true;

    @Column(name = "weekly_summary")
    @Builder.Default
    private boolean weeklySummary = true;
}