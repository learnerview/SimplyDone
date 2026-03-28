-- Insert another user key for multi-tenant testing
INSERT INTO api_keys (id, api_key, producer, active, is_admin, created_at)
VALUES ('33333333-3333-3333-3333-333333333333', 'sd_sk_test_user2', 'test-tenant-2', true, false, CURRENT_TIMESTAMP);
