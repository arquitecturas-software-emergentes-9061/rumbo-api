-- Beacon inventory metadata used by the administrative web (US-14): display name,
-- iBeacon-style UUID/major/minor, calibrated Tx power and unplaced beacons.
-- The legacy positioning_beacons name stays for Flyway compatibility.
ALTER TABLE positioning_beacons
    ADD COLUMN name varchar(100),
    ADD COLUMN proximity_uuid uuid,
    ADD COLUMN major integer,
    ADD COLUMN minor integer,
    ADD COLUMN tx_power_dbm integer;

-- A registered beacon may exist before it has a position on the plan.
ALTER TABLE positioning_beacons
    ALTER COLUMN x DROP NOT NULL,
    ALTER COLUMN y DROP NOT NULL;

ALTER TABLE positioning_beacons
    ADD CONSTRAINT ck_positioning_beacons_placement
        CHECK ((x IS NULL) = (y IS NULL)),
    ADD CONSTRAINT ck_positioning_beacons_active_requires_placement
        CHECK (NOT active OR x IS NOT NULL),
    ADD CONSTRAINT ck_positioning_beacons_ibeacon
        CHECK ((proximity_uuid IS NULL) = (major IS NULL) AND (major IS NULL) = (minor IS NULL)),
    ADD CONSTRAINT ck_positioning_beacons_major CHECK (major IS NULL OR major BETWEEN 0 AND 65535),
    ADD CONSTRAINT ck_positioning_beacons_minor CHECK (minor IS NULL OR minor BETWEEN 0 AND 65535),
    ADD CONSTRAINT ck_positioning_beacons_tx_power CHECK (tx_power_dbm IS NULL OR tx_power_dbm BETWEEN -120 AND 0);

CREATE UNIQUE INDEX uq_positioning_beacons_site_ibeacon
    ON positioning_beacons (site_id, proximity_uuid, major, minor)
    WHERE proximity_uuid IS NOT NULL;
