package com.uitopic.rumbo.planning.infrastructure.persistence.jdbc;

import com.uitopic.rumbo.planning.application.internal.exceptions.ScaleReferenceNotFoundException;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ScaleReference;
import com.uitopic.rumbo.planning.domain.repositories.ScaleReferenceRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persists measurement provenance and the draft plan scale in one transaction. */
@Repository
public class ScaleReferenceRepositoryImpl implements ScaleReferenceRepository {
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public ScaleReferenceRepositoryImpl(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  public boolean existsOnDifferentFloor(UUID siteId, UUID planVersionId, UUID floorId) {
    Integer count = jdbc.queryForObject("""
        SELECT count(*) FROM planning_scale_reference_measurements
        WHERE site_id = ? AND plan_version_id = ? AND floor_id <> ?
        """, Integer.class, siteId, planVersionId, floorId);
    return count != null && count > 0;
  }

  @Override
  public Optional<ScaleReference> upsert(ScaleReference reference, UUID modelVersionId) {
    var saved = upsertOriginal(reference);
    if (saved.isEmpty()) return saved;
    int changed = jdbc.update("""
        UPDATE plan_coordinate_spaces SET meters_per_unit = ?
        WHERE plan_version_id = ? AND model_version_id = ? AND floor_id = ?
        """, reference.metersPerUnit(), reference.planVersionId(), modelVersionId,
        reference.floorId());
    if (changed != 1) throw new ScaleReferenceNotFoundException();
    return saved;
  }

  @Override
  public Optional<ScaleReference> upsertOriginal(ScaleReference reference) {
    return jdbc.query("""
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
        """, ScaleReferenceRepositoryImpl::map,
        reference.siteId(), reference.floorId(), reference.planVersionId(),
        reference.unit().name(), reference.measuredDistance(), reference.startX(),
        reference.startY(), reference.endX(), reference.endY(), reference.metersPerUnit(),
        Timestamp.from(Instant.now(clock))).stream().findFirst();
  }

  @Override
  public Optional<ScaleReference> find(UUID siteId, UUID planVersionId) {
    return jdbc.query("""
        SELECT site_id, floor_id, plan_version_id, physical_unit, measured_distance,
               start_x, start_y, end_x, end_y, meters_per_unit
        FROM planning_scale_reference_measurements WHERE site_id = ? AND plan_version_id = ?
        """, ScaleReferenceRepositoryImpl::map, siteId, planVersionId)
        .stream().findFirst();
  }

  private static ScaleReference map(ResultSet rs, int ignored) throws SQLException {
    return new ScaleReference(rs.getObject("site_id", UUID.class),
        rs.getObject("floor_id", UUID.class), rs.getObject("plan_version_id", UUID.class),
        ScaleReference.Unit.valueOf(rs.getString("physical_unit")),
        rs.getDouble("measured_distance"), rs.getDouble("start_x"), rs.getDouble("start_y"),
        rs.getDouble("end_x"), rs.getDouble("end_y"), rs.getDouble("meters_per_unit"));
  }
}
