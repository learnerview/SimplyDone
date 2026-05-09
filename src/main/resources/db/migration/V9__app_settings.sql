CREATE TABLE app_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(1024) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO app_settings (setting_key, setting_value)
VALUES ('email_verification_enabled', 'true');