-- Email verification and OTP management for self-service registration
CREATE TABLE email_verifications (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    otp_code VARCHAR(6) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    verification_attempts INT NOT NULL DEFAULT 0,
    organization_name VARCHAR(255),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    verified_at TIMESTAMP(6) WITH TIME ZONE
);

CREATE INDEX idx_email_verified ON email_verifications(email, verified);
CREATE INDEX idx_expires_at ON email_verifications(expires_at);
