ALTER TABLE app_user
    ADD COLUMN phone VARCHAR(32);

ALTER TABLE app_user
    ADD COLUMN openid VARCHAR(128);

ALTER TABLE app_user
    ADD COLUMN provider VARCHAR(64);

ALTER TABLE app_user
    ADD COLUMN avatar_url TEXT;

ALTER TABLE app_user
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE app_user
    ADD COLUMN last_login_at TIMESTAMP WITH TIME ZONE;

CREATE UNIQUE INDEX uk_app_user_phone
    ON app_user (phone)
    WHERE phone IS NOT NULL;

CREATE TABLE access_token (
    token VARCHAR(128) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_access_token_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE INDEX idx_access_token_user_created_at
    ON access_token (user_id, created_at DESC);
