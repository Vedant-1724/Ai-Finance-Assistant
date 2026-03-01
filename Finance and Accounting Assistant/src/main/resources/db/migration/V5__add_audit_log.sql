-- PATH: finance-backend/src/main/resources/db/migration/V5__add_audit_log.sql
--
-- Creates the audit_log table required by AuditService.java
-- Stores immutable record of every transaction create/update/delete.
-- Required for financial compliance (RBI, GST audits).

CREATE TABLE IF NOT EXISTS audit_log (
    id           BIGSERIAL     PRIMARY KEY,
    user_id      BIGINT        NOT NULL REFERENCES users(id),
    company_id   BIGINT,
    action       VARCHAR(50)   NOT NULL,  -- CREATE_TRANSACTION, DELETE_TRANSACTION, LOGIN, etc.
    entity_type  VARCHAR(50),             -- Transaction, User, Payment, etc.
    entity_id    BIGINT,                  -- ID of the affected record
    detail       TEXT,                    -- JSON snapshot of what changed
    ip_address   VARCHAR(45),             -- IPv4 or IPv6
    user_agent   VARCHAR(500),
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_user       ON audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_company    ON audit_log(company_id);
CREATE INDEX IF NOT EXISTS idx_audit_created_at ON audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_action     ON audit_log(action);
