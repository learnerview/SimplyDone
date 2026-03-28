-- Allow multiple API keys per producer (for key rotation, team members, etc.)
ALTER TABLE api_keys DROP CONSTRAINT IF EXISTS api_keys_producer_key;

-- Restore admin key if it was rotated during testing
UPDATE api_keys SET api_key = 'sd_sk_test_admin', label = 'Default Admin', active = true, is_admin = true
WHERE producer = 'test-tenant-admin';
