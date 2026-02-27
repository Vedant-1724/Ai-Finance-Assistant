-- V3__add_jwt_auth.sql
-- Flyway migration: prepares the users table for BCrypt password hashing.
--
-- Changes:
--   1. Widens the 'password' column from VARCHAR(50) to VARCHAR(255)
--      so it can store 60-character BCrypt hashes.
--   2. Replaces the plain-text test password 'password123' with its
--      BCrypt hash (cost factor 10) so the existing admin account still works.
--
-- Place this file at:
--   src/main/resources/db/migration/V3__add_jwt_auth.sql

-- Step 1 — widen the password column
ALTER TABLE users
    ALTER COLUMN password TYPE VARCHAR(255);

-- Step 2 — replace the plain-text test password with BCrypt hash of 'password123'
-- BCrypt hash generated with cost factor 10.
-- The plain password for this account remains: password123
UPDATE users
SET password = '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW'
WHERE email = 'admin@finance.com';
