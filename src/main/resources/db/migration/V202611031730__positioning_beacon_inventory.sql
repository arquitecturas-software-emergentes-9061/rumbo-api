-- Registered sites and BLE beacons. Site ownership is account-scoped until the clinic
-- membership/onboarding model is implemented. No anonymous or cross-account reads.
CREATE TABLE indoor_sites (
    id uuid PRIMARY KEY,
    owner_user_id uuid NOT NULL,
    name varchar(120) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_indoor_sites_owner FOREIGN KEY (owner_user_id) REFERENCES identity_users(id)
);
CREATE INDEX ix_indoor_sites_owner ON indoor_sites (owner_user_id);

CREATE TABLE positioning_beacons (
    id uuid PRIMARY KEY,
    site_id uuid NOT NULL REFERENCES indoor_sites(id) ON DELETE CASCADE,
    floor_id uuid NOT NULL,
    identifier varchar(100) NOT NULL,
    x double precision NOT NULL,
    y double precision NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_positioning_beacons_site_identifier UNIQUE (site_id, identifier),
    CONSTRAINT ck_positioning_beacons_x CHECK (x >= 0 AND x < 'Infinity'::float8),
    CONSTRAINT ck_positioning_beacons_y CHECK (y >= 0 AND y < 'Infinity'::float8)
);
CREATE INDEX ix_positioning_beacons_site_floor ON positioning_beacons (site_id, floor_id);
