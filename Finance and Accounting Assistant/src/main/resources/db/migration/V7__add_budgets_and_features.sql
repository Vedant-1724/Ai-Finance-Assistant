-- ============================================================
-- V7__add_budgets_and_features.sql
-- PATH: Finance and Accounting Assistant/src/main/resources/db/migration/
-- ============================================================
-- Adds: budgets, recurring_transactions, audit_logs,
--       financial_health_scores, user_email_prefs,
--       company_members tables + gst_rate to categories

-- ── 1. Budgets ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS budgets (
    id          BIGSERIAL     PRIMARY KEY,
    company_id  BIGINT        NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    category_id BIGINT        REFERENCES categories(id) ON DELETE SET NULL,
    month       DATE          NOT NULL,   -- first day of month, e.g. 2026-03-01
    amount      NUMERIC(19,4) NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);
-- Backfill month column if table was pre-created without it
ALTER TABLE budgets ADD COLUMN IF NOT EXISTS month DATE;
CREATE UNIQUE INDEX IF NOT EXISTS idx_budget_company_category_month
    ON budgets(company_id, COALESCE(category_id, 0), month);
CREATE INDEX IF NOT EXISTS idx_budget_company_month ON budgets(company_id, month);

-- ── 2. Recurring Transactions ─────────────────────────────────────────────────
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS is_recurring          BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS recurrence_interval   VARCHAR(20),   -- DAILY/WEEKLY/MONTHLY/YEARLY
    ADD COLUMN IF NOT EXISTS recurrence_end_date   DATE,
    ADD COLUMN IF NOT EXISTS parent_transaction_id BIGINT REFERENCES transactions(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_txn_recurring
    ON transactions(company_id, is_recurring, recurrence_end_date)
    WHERE is_recurring = TRUE;

-- ── 3. Audit Logs ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS audit_logs (
    id          BIGSERIAL    PRIMARY KEY,
    company_id  BIGINT       REFERENCES companies(id) ON DELETE CASCADE,
    user_id     BIGINT       REFERENCES users(id)     ON DELETE SET NULL,
    action      VARCHAR(100) NOT NULL,   -- CREATE_TRANSACTION, DELETE_TRANSACTION, etc.
    entity_type VARCHAR(50),
    entity_id   BIGINT,
    old_value   TEXT,
    new_value   TEXT,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_audit_company_date ON audit_logs(company_id, created_at DESC);

-- ── 4. Financial Health Scores ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS financial_health_scores (
    id           BIGSERIAL     PRIMARY KEY,
    company_id   BIGINT        NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    month        DATE          NOT NULL,
    score        INTEGER       NOT NULL CHECK (score >= 0 AND score <= 100),
    breakdown    TEXT,          -- JSON string
    recommendations TEXT,       -- AI-generated
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_health_score_company_month
    ON financial_health_scores(company_id, month);

-- ── 5. User Email Preferences ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_email_prefs (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT    NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    anomaly_alerts    BOOLEAN   NOT NULL DEFAULT TRUE,
    forecast_alerts   BOOLEAN   NOT NULL DEFAULT TRUE,
    budget_alerts     BOOLEAN   NOT NULL DEFAULT TRUE,
    weekly_summary    BOOLEAN   NOT NULL DEFAULT TRUE,
    trial_reminders   BOOLEAN   NOT NULL DEFAULT TRUE,
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ── 6. Company Members (Team Access) ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS company_members (
    id          BIGSERIAL    PRIMARY KEY,
    company_id  BIGINT       NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    user_id     BIGINT       REFERENCES users(id) ON DELETE CASCADE,
    role        VARCHAR(20)  NOT NULL DEFAULT 'VIEWER',  -- OWNER / EDITOR / VIEWER
    invite_email VARCHAR(255),
    invite_token VARCHAR(100) UNIQUE,
    invite_expires_at TIMESTAMP,
    accepted_at TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_member_company_user
    ON company_members(company_id, user_id) WHERE user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_member_invite_token
    ON company_members(invite_token) WHERE invite_token IS NOT NULL;

-- ── 7. GST rate column on categories ─────────────────────────────────────────
ALTER TABLE categories
    ADD COLUMN IF NOT EXISTS gst_rate DECIMAL(5,2) NOT NULL DEFAULT 18.00
        CHECK (gst_rate IN (0.00, 5.00, 12.00, 18.00, 28.00));

-- ── 8. Seed default GST rates ────────────────────────────────────────────────
UPDATE categories SET gst_rate = 0  WHERE LOWER(name) IN ('salary','wages','rent','healthcare');
UPDATE categories SET gst_rate = 5  WHERE LOWER(name) IN ('food','groceries','transport');
UPDATE categories SET gst_rate = 12 WHERE LOWER(name) IN ('services','consulting');
