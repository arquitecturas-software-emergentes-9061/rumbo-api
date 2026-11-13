-- Profiles and Preferences (TS-33): one profile per IAM account, created after the registration commits.
-- user_id references identity_users.id by value only: no foreign key across bounded contexts.
-- phone_number and the image columns stay NULL until the user fills them in.

CREATE TABLE profiles_user_profiles (
    id                uuid          PRIMARY KEY,
    user_id           uuid          NOT NULL,
    full_name         varchar(255)  NOT NULL,
    role              varchar(20)   NOT NULL,
    phone_number      varchar(30),
    image_url         varchar(2048),
    image_storage_key varchar(255),
    created_at        timestamptz   NOT NULL,
    updated_at        timestamptz   NOT NULL,
    CONSTRAINT uk_profiles_user_profiles_user_id UNIQUE (user_id),
    CONSTRAINT ck_profiles_user_profiles_role CHECK (role IN ('CLINIC_ADMIN', 'PATIENT'))
);
