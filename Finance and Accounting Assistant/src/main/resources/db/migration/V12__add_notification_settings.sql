-- V12__add_notification_settings.sql

CREATE TABLE IF NOT EXISTS notification_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    anomaly_alerts BOOLEAN NOT NULL DEFAULT true,
    forecast_alerts BOOLEAN NOT NULL DEFAULT true,
    budget_alerts BOOLEAN NOT NULL DEFAULT true,
    trial_reminders BOOLEAN NOT NULL DEFAULT true,
    weekly_summary BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT fk_notification_settings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_notification_settings_user_id ON notification_settings (user_id);

-- Backfill default settings for existing users
INSERT INTO notification_settings (user_id)
SELECT id FROM users
ON CONFLICT (user_id) DO NOTHING;
