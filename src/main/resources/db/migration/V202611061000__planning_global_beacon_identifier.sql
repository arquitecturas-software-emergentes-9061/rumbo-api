-- Protect the preflight and normalization from concurrent inventory writes.
LOCK TABLE positioning_beacons IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM positioning_beacons
    GROUP BY lower(btrim(identifier, E' \t\r\n\f' || chr(11)) COLLATE "C")
    HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION 'Cannot enforce global BLE uniqueness: historical identifiers conflict'
      USING HINT = 'Review the conflicting inventory records and resolve their identifiers before retrying Flyway.';
  END IF;
END $$;

UPDATE positioning_beacons
SET identifier = lower(btrim(identifier, E' \t\r\n\f' || chr(11)) COLLATE "C")
WHERE identifier <> lower(btrim(identifier, E' \t\r\n\f' || chr(11)) COLLATE "C");

ALTER TABLE positioning_beacons
  ADD CONSTRAINT ck_positioning_beacons_canonical_identifier
    CHECK (identifier COLLATE "C" ~ '^[a-z0-9:_-]{4,100}$'),
  ADD CONSTRAINT uq_positioning_beacons_identifier UNIQUE (identifier),
  DROP CONSTRAINT uq_positioning_beacons_site_identifier;
