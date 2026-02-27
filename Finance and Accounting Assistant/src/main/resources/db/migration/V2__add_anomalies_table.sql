CREATE TABLE IF NOT EXISTS anomalies (
                                         id             BIGSERIAL PRIMARY KEY,
                                         company_id     BIGINT        NOT NULL,
                                         transaction_id BIGINT,
                                         amount         NUMERIC(19,4),
    detected_at    TIMESTAMP     NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_anomaly_company     ON anomalies (company_id);
CREATE INDEX IF NOT EXISTS idx_anomaly_detected_at ON anomalies (detected_at);