-- Add administrative structure without rewriting existing spatial ownership or IAM.
CREATE TABLE planning_clinics (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL UNIQUE REFERENCES identity_users(id),
    name VARCHAR(160) NOT NULL,
    code VARCHAR(60) NOT NULL,
    contact_email VARCHAR(254) NOT NULL,
    phone VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_planning_clinics_id_owner UNIQUE (id, owner_user_id)
);
ALTER TABLE indoor_sites ADD COLUMN clinic_id UUID;
ALTER TABLE indoor_sites ADD COLUMN address VARCHAR(200);
ALTER TABLE indoor_sites ADD CONSTRAINT fk_indoor_sites_clinic_owner
    FOREIGN KEY (clinic_id, owner_user_id) REFERENCES planning_clinics(id, owner_user_id);
CREATE INDEX ix_indoor_sites_clinic ON indoor_sites(clinic_id);
ALTER TABLE planning_floor_sites ADD CONSTRAINT uq_planning_floor_site UNIQUE (floor_id, site_id);
CREATE TABLE planning_floors (
    id UUID PRIMARY KEY,
    site_id UUID NOT NULL REFERENCES indoor_sites(id),
    floor_number INTEGER NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(250),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_planning_floors_level UNIQUE (site_id, floor_number),
    CONSTRAINT fk_planning_floors_ownership FOREIGN KEY (id, site_id)
        REFERENCES planning_floor_sites(floor_id, site_id)
);
