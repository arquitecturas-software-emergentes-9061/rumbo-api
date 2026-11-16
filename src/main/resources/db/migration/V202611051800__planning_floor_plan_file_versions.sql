CREATE TABLE planning_floor_plan_file_versions (
  id UUID PRIMARY KEY,
  floor_id UUID NOT NULL REFERENCES planning_floor_sites(floor_id),
  version_number INTEGER NOT NULL CHECK (version_number > 0),
  file_name VARCHAR(255) NOT NULL,
  media_type VARCHAR(32) NOT NULL CHECK (media_type IN ('application/pdf', 'image/svg+xml', 'image/png', 'image/jpeg')),
  file_bytes BYTEA NOT NULL CHECK (octet_length(file_bytes) BETWEEN 1 AND 26214400),
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_planning_floor_plan_file_version UNIQUE (floor_id, version_number)
);
