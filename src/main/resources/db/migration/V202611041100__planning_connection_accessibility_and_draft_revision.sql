-- Gabriela's published-route accessibility and optimistic draft editing contract.
-- IF NOT EXISTS also permits upgrading a copied database that already applied legacy V6/V7.
ALTER TABLE map_connections
  ADD COLUMN IF NOT EXISTS accessibility_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN'
  CHECK (accessibility_status IN ('UNKNOWN', 'ACCESSIBLE', 'INACCESSIBLE'));

ALTER TABLE map_versions
  ADD COLUMN IF NOT EXISTS edit_revision BIGINT NOT NULL DEFAULT 0
  CHECK (edit_revision >= 0);
