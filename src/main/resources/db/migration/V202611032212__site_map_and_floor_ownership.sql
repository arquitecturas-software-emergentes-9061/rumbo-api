CREATE UNIQUE INDEX one_indoor_map_per_site ON indoor_maps(site_id);

CREATE TABLE planning_floor_sites (
  floor_id UUID PRIMARY KEY,
  site_id UUID NOT NULL
);

INSERT INTO planning_floor_sites (floor_id, site_id)
SELECT DISTINCT p.floor_id, m.site_id
FROM plan_coordinate_spaces p
JOIN map_versions v ON v.id = p.model_version_id
JOIN indoor_maps m ON m.id = v.map_id;
