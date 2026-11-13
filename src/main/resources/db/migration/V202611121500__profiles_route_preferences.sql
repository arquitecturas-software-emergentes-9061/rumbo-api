ALTER TABLE profiles_user_profiles
    ADD COLUMN avoid_stairs boolean NOT NULL DEFAULT false,
    ADD COLUMN simpler_route boolean NOT NULL DEFAULT false,
    ADD COLUMN voice_guidance boolean NOT NULL DEFAULT false,
    ADD COLUMN preferences_updated_at timestamptz NULL;
