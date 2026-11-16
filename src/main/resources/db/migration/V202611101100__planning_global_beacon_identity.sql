-- Keep the existing inventory intact if two sites registered the same physical beacon.
-- Flyway runs this PostgreSQL migration in one transaction.
LOCK TABLE positioning_beacons IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM positioning_beacons
    GROUP BY lower(btrim(identifier, E' \t\r\n\f' || chr(11)) COLLATE "C")
    HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION 'Cannot enforce global BLE uniqueness: historical identifiers conflict'
      USING HINT = 'Resolve duplicate inventory identifiers before retrying Flyway.';
  END IF;
  IF EXISTS (
    SELECT 1 FROM positioning_beacons WHERE proximity_uuid IS NOT NULL
    GROUP BY proximity_uuid, major, minor HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION 'Cannot enforce global BLE uniqueness: historical iBeacon triplets conflict'
      USING HINT = 'Resolve duplicate physical beacon assignments before retrying Flyway.';
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

DROP INDEX uq_positioning_beacons_site_ibeacon;
CREATE UNIQUE INDEX uq_positioning_beacons_ibeacon
  ON positioning_beacons (proximity_uuid, major, minor)
  WHERE proximity_uuid IS NOT NULL;
