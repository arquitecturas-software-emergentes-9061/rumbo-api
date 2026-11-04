CREATE UNIQUE INDEX one_published_version_per_indoor_map
  ON map_versions(map_id) WHERE status = 'PUBLISHED';
