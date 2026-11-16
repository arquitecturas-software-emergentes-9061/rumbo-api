CREATE TABLE map_walls (
  id UUID NOT NULL,
  model_version_id UUID NOT NULL REFERENCES map_versions(id) ON DELETE CASCADE,
  floor_id UUID NOT NULL,
  plan_version_id UUID NOT NULL,
  x1 DOUBLE PRECISION NOT NULL,
  y1 DOUBLE PRECISION NOT NULL,
  x2 DOUBLE PRECISION NOT NULL,
  y2 DOUBLE PRECISION NOT NULL,
  PRIMARY KEY (id, model_version_id),
  FOREIGN KEY (plan_version_id, model_version_id, floor_id)
    REFERENCES plan_coordinate_spaces(plan_version_id, model_version_id, floor_id)
);

CREATE TABLE map_doors (
  id UUID NOT NULL,
  model_version_id UUID NOT NULL REFERENCES map_versions(id) ON DELETE CASCADE,
  wall_id UUID NOT NULL,
  from_fraction DOUBLE PRECISION NOT NULL CHECK (from_fraction >= 0),
  to_fraction DOUBLE PRECISION NOT NULL CHECK (to_fraction <= 1 AND to_fraction > from_fraction),
  PRIMARY KEY (id, model_version_id),
  FOREIGN KEY (wall_id, model_version_id) REFERENCES map_walls(id, model_version_id)
);
