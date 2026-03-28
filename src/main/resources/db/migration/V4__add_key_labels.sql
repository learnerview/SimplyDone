-- Add a label field to api_keys for identification in the Admin UI
ALTER TABLE api_keys ADD COLUMN label VARCHAR(255);
UPDATE api_keys SET label = 'Default Admin' WHERE api_key = 'sd_sk_test_admin';
UPDATE api_keys SET label = 'Test Tenant 1' WHERE api_key = 'sd_sk_test_user1';
UPDATE api_keys SET label = 'Test Tenant 2' WHERE api_key = 'sd_sk_test_user2';
