-- V2__add_anomalies_table.sql
-- Flyway migration: adds the anomalies table required by Anomaly.java entity.
-- Place this file at:
--   finance-backend/src/main/resources/db/migration/V2__add_anomalies_table.sql

CREATE TABLE IF NOT EXISTS anomalies (
                                         id             BIGSERIAL    PRIMARY KEY,
                                         company_id     BIGINT       NOT NULL REFERENCES companies(id),
    transaction_id BIGINT       REFERENCES transactions(id) ON DELETE SET NULL,
    amount         NUMERIC(19,4),
    detected_at    TIMESTAMP    NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_anomaly_company     ON anomalies(company_id);
CREATE INDEX IF NOT EXISTS idx_anomaly_detected_at ON anomalies(detected_at);