-- PATH: finance-backend/src/main/resources/db/migration/V5__free_tier.sql
--
-- Introduces FREE tier as the default subscription status.
-- Existing TRIAL users who have never used their trial are reset to FREE.
-- Existing users who HAVE started their trial keep TRIAL status.
-- Adds AI chat daily usage tracking columns.

-- Change default for new users to FREE
ALTER TABLE users
    ALTER COLUMN subscription_status SET DEFAULT 'FREE';

-- Reset users who are TRIAL but never actually started (trial_started_at is null or was auto-set in @PrePersist)
-- We keep TRIAL for users who actually have a trial_started_at set (they were real trial users)
-- New installs: all existing users move to FREE. Comment this out if you want to keep existing trial users.
UPDATE users
SET subscription_status = 'FREE',
    trial_started_at = NULL
WHERE subscription_status = 'TRIAL'
  AND (trial_started_at IS NULL OR trial_started_at >= NOW() - INTERVAL '1 minute');
-- ^^ The 1-minute window catches the auto-set @PrePersist values from new registrations.
-- Adjust to a wider window if needed (e.g., INTERVAL '1 day' to reset all trial users to FREE).

-- Add AI chat daily tracking columns
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS ai_chats_used_today  INT       NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS ai_chat_reset_date   DATE;

-- Index for fast subscription tier lookups
CREATE INDEX IF NOT EXISTS idx_users_sub_status_v2
    ON users(subscription_status);