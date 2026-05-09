CREATE TABLE api_keys (
    id VARCHAR(36) PRIMARY KEY,
    api_key VARCHAR(100) NOT NULL UNIQUE,
    producer VARCHAR(120) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    is_admin BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

-- Insert a default admin key
-- No default API keys are inserted in migrations to avoid committing secrets.
-- Create API keys via administrative tooling or runtime provisioning.
