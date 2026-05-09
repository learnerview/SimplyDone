-- Allow multiple API keys per producer (for key rotation, team members, etc.)
ALTER TABLE api_keys DROP CONSTRAINT IF EXISTS api_keys_producer_key;

-- Do not restore or inject admin keys in migrations. Use administrative tooling for key rotation.
