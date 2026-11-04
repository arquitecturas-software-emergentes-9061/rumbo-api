package com.uitopic.rumbo.planning.infrastructure.persistence.jdbc;

import com.uitopic.rumbo.planning.domain.model.valueobjects.DraftPlanBounds;
import com.uitopic.rumbo.planning.domain.repositories.PlanSpatialReferenceRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads the authoritative Planning floor-plan rows for spatial validation. */
@Repository
public class PlanSpatialReferenceRepositoryImpl implements PlanSpatialReferenceRepository {
  private final JdbcTemplate jdbc;

  public PlanSpatialReferenceRepositoryImpl(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<DraftPlanBounds> lockDraftPlan(UUID siteId, UUID floorId, UUID planVersionId) {
    return jdbc.query("""
        SELECT p.model_version_id, p.width_units, p.height_units
        FROM plan_coordinate_spaces p
        JOIN map_versions v ON v.id = p.model_version_id
        JOIN indoor_maps m ON m.id = v.map_id
        WHERE m.site_id = ? AND p.floor_id = ? AND p.plan_version_id = ?
          AND v.status = 'DRAFT'
        FOR UPDATE OF p
        """, (rs, ignored) -> new DraftPlanBounds(
        rs.getObject("model_version_id", UUID.class), rs.getDouble("width_units"),
        rs.getDouble("height_units")), siteId, floorId, planVersionId)
        .stream().findFirst();
  }

  @Override
  public boolean containsPoint(UUID siteId, UUID floorId, double xUnits, double yUnits) {
    Integer count = jdbc.queryForObject("""
        SELECT count(*) FROM plan_coordinate_spaces p
        JOIN map_versions v ON v.id = p.model_version_id
        JOIN indoor_maps m ON m.id = v.map_id
        WHERE m.site_id = ? AND p.floor_id = ?
          AND v.status IN ('DRAFT','PUBLISHED')
          AND ? BETWEEN 0 AND p.width_units
          AND ? BETWEEN 0 AND p.height_units
        """, Integer.class, siteId, floorId, xUnits, yUnits);
    return count != null && count > 0;
  }
}
