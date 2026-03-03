CREATE TABLE IF NOT EXISTS notification_settings (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users(id),
    anomaly_alerts    BOOLEAN NOT NULL DEFAULT TRUE,
    forecast_alerts   BOOLEAN NOT NULL DEFAULT TRUE,
    budget_alerts     BOOLEAN NOT NULL DEFAULT TRUE,
    trial_reminders   BOOLEAN NOT NULL DEFAULT TRUE,
    weekly_summary    BOOLEAN NOT NULL DEFAULT TRUE
    );