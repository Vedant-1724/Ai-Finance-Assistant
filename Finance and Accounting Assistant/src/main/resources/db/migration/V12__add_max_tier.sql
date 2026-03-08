-- V12__add_max_tier.sql
-- Add MAX to the subscription_status_enum

ALTER TYPE subscription_status_enum ADD VALUE IF NOT EXISTS 'MAX';
