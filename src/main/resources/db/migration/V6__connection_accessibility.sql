ALTER TABLE map_connections
  ADD COLUMN accessibility_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN'
  CHECK (accessibility_status IN ('UNKNOWN', 'ACCESSIBLE', 'INACCESSIBLE'));
