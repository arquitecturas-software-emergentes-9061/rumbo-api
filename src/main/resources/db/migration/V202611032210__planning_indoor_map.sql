CREATE TABLE indoor_maps (
  id UUID PRIMARY KEY,
  site_id UUID NOT NULL
);

CREATE TABLE map_versions (
  id UUID PRIMARY KEY,
  map_id UUID NOT NULL REFERENCES indoor_maps(id) ON DELETE CASCADE,
  status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE UNIQUE INDEX one_draft_per_indoor_map ON map_versions(map_id) WHERE status = 'DRAFT';

CREATE TABLE plan_coordinate_spaces (
  plan_version_id UUID NOT NULL,
  model_version_id UUID NOT NULL REFERENCES map_versions(id) ON DELETE CASCADE,
  floor_id UUID NOT NULL,
  width_units DOUBLE PRECISION NOT NULL CHECK (width_units > 0),
  height_units DOUBLE PRECISION NOT NULL CHECK (height_units > 0),
  meters_per_unit DOUBLE PRECISION CHECK (meters_per_unit > 0),
  PRIMARY KEY (plan_version_id, model_version_id),
  UNIQUE (plan_version_id, model_version_id, floor_id)
);

CREATE TABLE map_nodes (
  id UUID NOT NULL,
  model_version_id UUID NOT NULL REFERENCES map_versions(id) ON DELETE CASCADE,
  floor_id UUID NOT NULL,
  plan_version_id UUID NOT NULL,
  x DOUBLE PRECISION NOT NULL CHECK (x >= 0),
  y DOUBLE PRECISION NOT NULL CHECK (y >= 0),
  node_type VARCHAR(32) NOT NULL,
  PRIMARY KEY (id, model_version_id),
  FOREIGN KEY (plan_version_id, model_version_id, floor_id)
    REFERENCES plan_coordinate_spaces(plan_version_id, model_version_id, floor_id)
);

CREATE TABLE map_connections (
  id UUID NOT NULL,
  model_version_id UUID NOT NULL REFERENCES map_versions(id) ON DELETE CASCADE,
  source_node_id UUID NOT NULL,
  target_node_id UUID NOT NULL,
  connection_type VARCHAR(32) NOT NULL,
  distance_meters DOUBLE PRECISION NOT NULL CHECK (distance_meters > 0),
  weight DOUBLE PRECISION NOT NULL CHECK (weight > 0),
  bidirectional BOOLEAN NOT NULL,
  PRIMARY KEY (id, model_version_id),
  CHECK (source_node_id <> target_node_id),
  FOREIGN KEY (source_node_id, model_version_id) REFERENCES map_nodes(id, model_version_id),
  FOREIGN KEY (target_node_id, model_version_id) REFERENCES map_nodes(id, model_version_id)
);

CREATE INDEX map_versions_map_id_idx ON map_versions(map_id);
CREATE INDEX map_nodes_model_version_id_idx ON map_nodes(model_version_id);
CREATE INDEX map_connections_model_version_id_idx ON map_connections(model_version_id);
