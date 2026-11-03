CREATE TABLE map_destinations (
  id UUID NOT NULL,
  model_version_id UUID NOT NULL REFERENCES map_versions(id) ON DELETE CASCADE,
  node_id UUID NOT NULL,
  entry_type VARCHAR(16) NOT NULL CHECK (entry_type IN ('DOCTOR', 'SPECIALTY', 'SERVICE')),
  name VARCHAR(150) NOT NULL CHECK (length(trim(name)) > 0),
  specialty VARCHAR(150),
  search_text TEXT NOT NULL,
  PRIMARY KEY (id, model_version_id),
  FOREIGN KEY (node_id, model_version_id) REFERENCES map_nodes(id, model_version_id)
);

CREATE INDEX map_destinations_search_idx
  ON map_destinations USING GIN (to_tsvector('simple'::regconfig, search_text));
CREATE INDEX map_destinations_version_idx ON map_destinations(model_version_id);
