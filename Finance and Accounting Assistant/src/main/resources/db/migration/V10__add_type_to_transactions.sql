-- V10: Comprehensive catch-up migration
-- Adds ALL columns expected by the Transaction entity that are missing from DB.
-- Uses IF NOT EXISTS to be idempotent.

-- 1. Add 'type' column (INCOME / EXPENSE)
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS type VARCHAR(20);
UPDATE transactions SET type = 'INCOME'  WHERE amount >= 0 AND type IS NULL;
UPDATE transactions SET type = 'EXPENSE' WHERE amount <  0 AND type IS NULL;
ALTER TABLE transactions ALTER COLUMN type SET DEFAULT 'EXPENSE';
ALTER TABLE transactions ALTER COLUMN type SET NOT NULL;

-- 2. Add 'user_id' column
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS user_id BIGINT;

-- 3. Add 'reference_number' column
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS reference_number VARCHAR(100);

-- 4. Add recurring fields
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS is_recurring BOOLEAN DEFAULT FALSE;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS recurring_interval VARCHAR(50);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS recurrence_interval VARCHAR(50);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS recurrence_end_date DATE;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS parent_transaction_id BIGINT;

-- 5. Add AI categorization fields
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS ai_categorized BOOLEAN DEFAULT FALSE;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS ai_confidence DOUBLE PRECISION;

-- 6. Add anomaly fields
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS is_anomaly BOOLEAN DEFAULT FALSE;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS anomaly_reason VARCHAR(255);

-- 7. Add updated_at timestamp
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITHOUT TIME ZONE;

-- 8. Add account column
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS account VARCHAR(100);

-- 9. Make 'description' nullable (entity doesn't require it for imports)
ALTER TABLE transactions ALTER COLUMN description DROP NOT NULL;

-- 10. Add foreign key for user_id if not exists
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_transactions_user'
    ) THEN
        ALTER TABLE transactions
            ADD CONSTRAINT fk_transactions_user
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;
    END IF;
END $$;

-- 11. Ensure budgets table exists (V7 may not have run)
CREATE TABLE IF NOT EXISTS budgets (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT  NOT NULL REFERENCES companies(id),
    category_id BIGINT  REFERENCES categories(id),
    name        VARCHAR(200),
    amount      NUMERIC(15,2) NOT NULL,
    period      VARCHAR(20) DEFAULT 'MONTHLY',
    start_date  DATE,
    end_date    DATE,
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

-- 12. Ensure health_scores table exists (V8 may not have run)
CREATE TABLE IF NOT EXISTS health_scores (
    id                BIGSERIAL PRIMARY KEY,
    company_id        BIGINT NOT NULL REFERENCES companies(id),
    month             DATE NOT NULL,
    overall_score     INT,
    profit_score      INT,
    expense_score     INT,
    income_score      INT,
    recommendations   TEXT,
    created_at        TIMESTAMP DEFAULT NOW()
);

-- 13. Ensure company_members table has all needed columns
ALTER TABLE company_members ADD COLUMN IF NOT EXISTS invite_email VARCHAR(255);
ALTER TABLE company_members ADD COLUMN IF NOT EXISTS invite_token VARCHAR(255);
ALTER TABLE company_members ADD COLUMN IF NOT EXISTS invite_expires_at TIMESTAMP;
ALTER TABLE company_members ADD COLUMN IF NOT EXISTS accepted_at TIMESTAMP;

-- 14. Ensure user_email_prefs table exists
CREATE TABLE IF NOT EXISTS user_email_prefs (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    anomaly_alerts      BOOLEAN DEFAULT TRUE,
    forecast_warnings   BOOLEAN DEFAULT TRUE,
    budget_alerts       BOOLEAN DEFAULT TRUE,
    monthly_report      BOOLEAN DEFAULT TRUE,
    team_invites        BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMP DEFAULT NOW()
);
