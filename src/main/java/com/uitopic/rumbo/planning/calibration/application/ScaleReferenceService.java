package com.uitopic.rumbo.planning.calibration.application;

import com.uitopic.rumbo.planning.calibration.domain.ScaleReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ScaleReferenceService {
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public ScaleReferenceService(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc; this.clock = clock;
  }

  public static class NotFound extends RuntimeException {
    public NotFound() { super("Plan scale or site not found"); }
  }
  public static class FloorConflict extends RuntimeException {
    public FloorConflict() { super("Plan version already belongs to a different floor"); }
  }

  private record Bounds(UUID modelVersionId, double width, double height) {}

  @Transactional
  public ScaleReference upsert(UUID userId, ScaleReference reference) {
    requireSiteOwner(userId, reference.siteId());
    Integer previousFloor = jdbc.queryForObject("""
      SELECT count(*) FROM planning_scale_reference_measurements
      WHERE site_id = ? AND plan_version_id = ? AND floor_id <> ?
      """, Integer.class, reference.siteId(), reference.planVersionId(), reference.floorId());
    if (previousFloor != null && previousFloor > 0) throw new FloorConflict();

    // The source of truth is the draft Planning map, not the measurement ledger.
    // Hold a row lock to avoid editing an archived or published version.
    List<Bounds> candidates = jdbc.query("""
      SELECT p.model_version_id, p.width_units, p.height_units
      FROM plan_coordinate_spaces p
      JOIN map_versions v ON v.id = p.model_version_id
      JOIN indoor_maps m ON m.id = v.map_id
      WHERE m.site_id = ? AND p.floor_id = ? AND p.plan_version_id = ?
        AND v.status = 'DRAFT'
      FOR UPDATE OF p
      """, (rs, i) -> new Bounds(rs.getObject("model_version_id", UUID.class),
        rs.getDouble("width_units"), rs.getDouble("height_units")),
      reference.siteId(), reference.floorId(), reference.planVersionId());
    if (candidates.size() != 1) throw new NotFound();
    Bounds bounds = candidates.getFirst();
    if (reference.startX() > bounds.width() || reference.endX() > bounds.width()
        || reference.startY() > bounds.height() || reference.endY() > bounds.height()) {
      throw new IllegalArgumentException("Calibration points are outside the referenced plan");
    }
    String sql = """
      INSERT INTO planning_scale_reference_measurements
        (site_id, floor_id, plan_version_id, physical_unit, measured_distance,
         start_x, start_y, end_x, end_y, meters_per_unit, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT (site_id, plan_version_id) DO UPDATE SET
        physical_unit = EXCLUDED.physical_unit,
        measured_distance = EXCLUDED.measured_distance,
        start_x = EXCLUDED.start_x, start_y = EXCLUDED.start_y,
        end_x = EXCLUDED.end_x, end_y = EXCLUDED.end_y,
        meters_per_unit = EXCLUDED.meters_per_unit,
        updated_at = EXCLUDED.updated_at
      WHERE planning_scale_reference_measurements.floor_id = EXCLUDED.floor_id
      RETURNING site_id, floor_id, plan_version_id, physical_unit, measured_distance,
        start_x, start_y, end_x, end_y, meters_per_unit
      """;
    List<ScaleReference> saved = jdbc.query(sql, ScaleReferenceService::map,
      reference.siteId(), reference.floorId(), reference.planVersionId(),
      reference.unit().name(), reference.measuredDistance(),
      reference.startX(), reference.startY(), reference.endX(), reference.endY(),
      reference.metersPerUnit(), java.sql.Timestamp.from(Instant.now(clock)));
    if (saved.isEmpty()) throw new FloorConflict();
    int changed = jdbc.update("""
      UPDATE plan_coordinate_spaces SET meters_per_unit = ?
      WHERE plan_version_id = ? AND model_version_id = ? AND floor_id = ?
      """, reference.metersPerUnit(), reference.planVersionId(),
      bounds.modelVersionId(), reference.floorId());
    if (changed != 1) throw new NotFound();
    return saved.getFirst();
  }

  @Transactional(readOnly = true)
  public ScaleReference find(UUID userId, UUID siteId, UUID planVersionId) {
    requireSiteOwner(userId, siteId);
    return jdbc.query("""
      SELECT site_id, floor_id, plan_version_id, physical_unit, measured_distance,
             start_x, start_y, end_x, end_y, meters_per_unit
      FROM planning_scale_reference_measurements WHERE site_id = ? AND plan_version_id = ?
      """, ScaleReferenceService::map, siteId, planVersionId)
      .stream().findFirst().orElseThrow(NotFound::new);
  }

  private void requireSiteOwner(UUID userId, UUID siteId) {
    if (userId == null || siteId == null) throw new NotFound();
    Integer count = jdbc.queryForObject(
      "SELECT count(*) FROM indoor_sites WHERE id = ? AND owner_user_id = ?",
      Integer.class, siteId, userId);
    if (count == null || count == 0) throw new NotFound();
  }

  private static ScaleReference map(java.sql.ResultSet rs, int idx) throws java.sql.SQLException {
    return new ScaleReference(rs.getObject("site_id", UUID.class),
      rs.getObject("floor_id", UUID.class),
      rs.getObject("plan_version_id", UUID.class),
      ScaleReference.Unit.valueOf(rs.getString("physical_unit")),
      rs.getDouble("measured_distance"), rs.getDouble("start_x"), rs.getDouble("start_y"),
      rs.getDouble("end_x"), rs.getDouble("end_y"), rs.getDouble("meters_per_unit"));
  }
}
