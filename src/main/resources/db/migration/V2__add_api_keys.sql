CREATE TABLE api_keys (
    id VARCHAR(36) PRIMARY KEY,
    api_key VARCHAR(100) NOT NULL UNIQUE,
    producer VARCHAR(120) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    is_admin BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

-- Insert a default admin key
INSERT INTO api_keys (id, api_key, producer, active, is_admin, created_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'sd_sk_test_admin', 'test-tenant-admin', true, true, CURRENT_TIMESTAMP);

-- Insert a default user key
INSERT INTO api_keys (id, api_key, producer, active, is_admin, created_at)
VALUES ('22222222-2222-2222-2222-222222222222', 'sd_sk_test_user1', 'test-tenant-1', true, false, CURRENT_TIMESTAMP);
