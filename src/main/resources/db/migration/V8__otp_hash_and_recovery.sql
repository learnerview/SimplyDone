-- Expand otp_code column to store SHA-256 hex hashes (64 chars) instead of plaintext OTPs (6 chars).
-- This ensures OTPs cannot be recovered from the database if it is breached.
ALTER TABLE email_verifications ALTER COLUMN otp_code TYPE VARCHAR(64);

-- Clear any pending (unhashed) OTP verifications from before this migration.
-- Users with pending verifications will need to request a new OTP.
DELETE FROM email_verifications WHERE verified = FALSE;
