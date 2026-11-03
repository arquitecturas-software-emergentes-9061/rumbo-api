ALTER TABLE map_connections
  ADD COLUMN vertical_penalty DOUBLE PRECISION NOT NULL DEFAULT 0
    CHECK (vertical_penalty >= 0);

ALTER TABLE map_connections
  ADD CONSTRAINT nonvertical_penalty_zero
    CHECK (connection_type IN ('STAIRS', 'RAMP', 'ELEVATOR') OR vertical_penalty = 0);
