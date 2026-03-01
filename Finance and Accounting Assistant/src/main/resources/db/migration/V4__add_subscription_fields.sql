-- PATH: finance-backend/src/main/resources/db/migration/V4__add_subscription_fields.sql
--
-- Adds subscription tracking columns to the users table.
-- Backed by User.java SubscriptionStatus enum and SubscriptionFilter.java.
--
-- Run order: V1 → V2 → V3 → V4 (this file) → V5

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS trial_started_at           TIMESTAMP,
    ADD COLUMN IF NOT EXISTS subscription_status        VARCHAR(20) NOT NULL DEFAULT 'TRIAL',
    ADD COLUMN IF NOT EXISTS subscription_expires_at    TIMESTAMP,
    ADD COLUMN IF NOT EXISTS razorpay_subscription_id   VARCHAR(255);

-- Back-fill: existing users start their trial now
UPDATE users
SET trial_started_at = NOW()
WHERE trial_started_at IS NULL;

-- Index speeds up subscription-gating queries
CREATE INDEX IF NOT EXISTS idx_users_sub_status
    ON users(subscription_status, subscription_expires_at);
