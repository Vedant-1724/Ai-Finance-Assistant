-- Verify all existing users so test accounts like the default admin can log in
UPDATE users SET email_verified = TRUE;
