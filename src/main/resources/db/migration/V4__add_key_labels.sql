-- Add a label field to api_keys for identification in the Admin UI
ALTER TABLE api_keys ADD COLUMN label VARCHAR(255);
-- Do not modify or assume specific demo keys here; labels can be added via admin UI or provisioning.
