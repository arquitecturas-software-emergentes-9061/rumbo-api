ALTER TABLE map_versions
  ADD COLUMN edit_revision BIGINT NOT NULL DEFAULT 0
  CHECK (edit_revision >= 0);
