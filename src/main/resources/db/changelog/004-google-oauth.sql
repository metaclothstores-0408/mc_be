--liquibase formatted sql

--changeset metacloth:004-google-oauth

CREATE TABLE google_oauth_accounts (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL
        REFERENCES users(id)
        ON DELETE CASCADE,

    provider VARCHAR(50) NOT NULL DEFAULT 'GOOGLE',

    provider_subject VARCHAR(255) NOT NULL,

    provider_email VARCHAR(255) NOT NULL,

    access_token_encrypted TEXT,

    refresh_token_encrypted TEXT,

    access_token_expires_at TIMESTAMPTZ,

    scopes TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_google_oauth_provider_subject
        UNIQUE (provider, provider_subject),

    CONSTRAINT uk_google_oauth_user_provider
        UNIQUE (user_id, provider)
);

CREATE INDEX idx_google_oauth_user_id
    ON google_oauth_accounts(user_id);

CREATE INDEX idx_google_oauth_provider_email
    ON google_oauth_accounts(provider_email);