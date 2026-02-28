-- V1__init.sql
-- Flyway baseline migration: creates all tables from scratch.
-- This runs first on a clean database (e.g. fresh Docker volume).
-- Place at: src/main/resources/db/migration/V1__init.sql

-- ── Users ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id         BIGSERIAL    PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(50)  NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ── Companies (multi-tenant) ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS companies (
    id         BIGSERIAL    PRIMARY KEY,
    owner_id   BIGINT       NOT NULL REFERENCES users(id),
    name       VARCHAR(255) NOT NULL,
    currency   VARCHAR(10)  NOT NULL DEFAULT 'USD',
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ── Accounts (bank accounts, credit cards) ────────────────────────────────────
CREATE TABLE IF NOT EXISTS accounts (
    id         BIGSERIAL     PRIMARY KEY,
    company_id BIGINT        NOT NULL REFERENCES companies(id),
    name       VARCHAR(255)  NOT NULL,
    type       VARCHAR(50)   NOT NULL,  -- CHECKING, SAVINGS, CREDIT
    balance    NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency   VARCHAR(10)   NOT NULL DEFAULT 'USD'
);

-- ── Categories ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS categories (
    id         BIGSERIAL    PRIMARY KEY,
    company_id BIGINT       REFERENCES companies(id),  -- NULL = global
    name       VARCHAR(255) NOT NULL,
    type       VARCHAR(20)  NOT NULL  -- INCOME, EXPENSE
);

-- ── Transactions ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS transactions (
    id          BIGSERIAL     PRIMARY KEY,
    company_id  BIGINT        NOT NULL REFERENCES companies(id),
    account_id  BIGINT        REFERENCES accounts(id),
    category_id BIGINT        REFERENCES categories(id),
    date        DATE          NOT NULL,
    amount      NUMERIC(19,4) NOT NULL,
    description VARCHAR(512)  NOT NULL,
    source      VARCHAR(50)   NOT NULL DEFAULT 'MANUAL',  -- PLAID, CSV, MANUAL
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_txn_company ON transactions(company_id);
CREATE INDEX IF NOT EXISTS idx_txn_date    ON transactions(company_id, date);

-- ── Invoices ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS invoices (
    id         BIGSERIAL     PRIMARY KEY,
    company_id BIGINT        NOT NULL REFERENCES companies(id),
    vendor     VARCHAR(255),
    total      NUMERIC(19,4),
    file_path  VARCHAR(1024),
    parsed_at  TIMESTAMP,
    created_at TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- ── Seed data ─────────────────────────────────────────────────────────────────
-- Password is BCrypt hash of 'password123' (cost factor 10)
INSERT INTO users (email, password, role)
VALUES ('admin@finance.com', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', 'ADMIN')
ON CONFLICT (email) DO NOTHING;

INSERT INTO companies (owner_id, name, currency)
SELECT id, 'My Company', 'USD'
FROM   users
WHERE  email = 'admin@finance.com'
ON CONFLICT DO NOTHING;
