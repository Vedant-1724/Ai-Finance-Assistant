CREATE TABLE IF NOT EXISTS bank_sync_consents (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    provider_key VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    state_token VARCHAR(120) NOT NULL UNIQUE,
    provider_consent_id VARCHAR(255),
    consent_url TEXT,
    mock_fallback BOOLEAN NOT NULL DEFAULT FALSE,
    consent_expires_at TIMESTAMP,
    last_synced_at TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bank_sync_consents_company_id
    ON bank_sync_consents(company_id);

CREATE INDEX IF NOT EXISTS idx_bank_sync_consents_state_token
    ON bank_sync_consents(state_token);
