-- PR-120: original physical reference measurements for a floor-plan version.
-- Kept in a separate provenance table: Planning's map-version aggregate still owns
-- the actual publishable meters_per_unit after its branch has been integrated.
CREATE TABLE planning_scale_reference_measurements (
  site_id uuid NOT NULL REFERENCES indoor_sites(id) ON DELETE CASCADE,
  floor_id uuid NOT NULL,
  plan_version_id uuid NOT NULL,
  physical_unit varchar(12) NOT NULL,
  measured_distance double precision NOT NULL,
  start_x double precision NOT NULL,
  start_y double precision NOT NULL,
  end_x double precision NOT NULL,
  end_y double precision NOT NULL,
  meters_per_unit double precision NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (site_id, plan_version_id),
  CONSTRAINT ck_planning_scale_unit CHECK (physical_unit IN ('METERS','CENTIMETERS')),
  CONSTRAINT ck_planning_scale_measurement CHECK (measured_distance > 0 AND measured_distance < 'Infinity'::float8),
  CONSTRAINT ck_planning_scale_factor CHECK (meters_per_unit > 0 AND meters_per_unit < 'Infinity'::float8),
  CONSTRAINT ck_planning_scale_start_x CHECK (start_x >= 0 AND start_x < 'Infinity'::float8),
  CONSTRAINT ck_planning_scale_start_y CHECK (start_y >= 0 AND start_y < 'Infinity'::float8),
  CONSTRAINT ck_planning_scale_end_x CHECK (end_x >= 0 AND end_x < 'Infinity'::float8),
  CONSTRAINT ck_planning_scale_end_y CHECK (end_y >= 0 AND end_y < 'Infinity'::float8),
  CONSTRAINT ck_planning_scale_distinct CHECK (start_x <> end_x OR start_y <> end_y)
);
CREATE INDEX ix_planning_scale_floor ON planning_scale_reference_measurements (site_id, floor_id);
