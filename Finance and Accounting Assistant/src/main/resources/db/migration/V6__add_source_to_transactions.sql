-- PATH: Finance and Accounting Assistant/src/main/resources/db/migration/
--       V6__add_source_to_transactions.sql

-- Add 'source' column to transactions if it doesn't already exist
-- This tracks where a transaction came from: MANUAL, CSV_IMPORT, PDF, UPI_SCREENSHOT, etc.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='transactions' AND column_name='source'
    ) THEN
ALTER TABLE transactions
    ADD COLUMN source VARCHAR(50) NOT NULL DEFAULT 'MANUAL';
END IF;
END $$;

COMMENT ON COLUMN transactions.source IS
    'Origin of transaction: MANUAL, CSV_IMPORT, PDF, UPI_SCREENSHOT, PLAID';