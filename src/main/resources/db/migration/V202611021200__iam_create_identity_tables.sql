-- Identity and Access Control (BC01): accounts and the sessions issued to them.
-- Personal data (name, phone, photo) lives in Profiles and Preferences, not here.

CREATE TABLE identity_users (
    id                    uuid         PRIMARY KEY,
    email                 varchar(254) NOT NULL,
    password_hash         varchar(255) NOT NULL,
    role                  varchar(20)  NOT NULL,
    status                varchar(20)  NOT NULL,
    failed_login_attempts integer      NOT NULL DEFAULT 0,
    locked_until          timestamptz,
    clinic_id             uuid,
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL,
    CONSTRAINT uk_identity_users_email UNIQUE (email),
    CONSTRAINT ck_identity_users_role CHECK (role IN ('CLINIC_ADMIN', 'PATIENT')),
    CONSTRAINT ck_identity_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    CONSTRAINT ck_identity_users_failed_attempts CHECK (failed_login_attempts >= 0),
    CONSTRAINT ck_identity_users_lock CHECK (status <> 'LOCKED' OR locked_until IS NOT NULL)
);

-- Set when Planning registers the clinic of an administrator (ClinicRegisteredEvent); null for patients.
CREATE INDEX ix_identity_users_clinic_id ON identity_users (clinic_id);

CREATE TABLE identity_auth_sessions (
    id                 uuid        PRIMARY KEY,
    user_id            uuid        NOT NULL REFERENCES identity_users (id) ON DELETE CASCADE,
    refresh_token_hash varchar(64) NOT NULL,
    expires_at         timestamptz NOT NULL,
    revoked_at         timestamptz,
    created_at         timestamptz NOT NULL,
    CONSTRAINT uk_identity_auth_sessions_refresh_token UNIQUE (refresh_token_hash)
);

CREATE INDEX ix_identity_auth_sessions_user_id ON identity_auth_sessions (user_id);
