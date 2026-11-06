package com.uitopic.rumbo.planning.infrastructure.persistence.jdbc;

import com.uitopic.rumbo.planning.domain.model.entities.FloorPlanVersion;
import com.uitopic.rumbo.planning.domain.model.valueobjects.FloorPlanFile;
import com.uitopic.rumbo.planning.domain.repositories.FloorPlanVersionRepository;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FloorPlanVersionRepositoryImpl implements FloorPlanVersionRepository {
  private final JdbcTemplate jdbc;

  public FloorPlanVersionRepositoryImpl(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean lockFloor(UUID siteId, UUID floorId) {
    return !jdbc.queryForList("""
      SELECT floor_id FROM planning_floor_sites WHERE floor_id = ? AND site_id = ? FOR UPDATE
      """, UUID.class, floorId, siteId).isEmpty();
  }

  @Override
  public int nextVersion(UUID floorId) {
    return jdbc.queryForObject("""
      SELECT COALESCE(MAX(version_number), 0) + 1
      FROM planning_floor_plan_file_versions WHERE floor_id = ?
      """, Integer.class, floorId);
  }

  @Override
  public void insert(FloorPlanVersion version, byte[] bytes) {
    jdbc.update("""
      INSERT INTO planning_floor_plan_file_versions
        (id, floor_id, version_number, file_name, media_type, file_bytes, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?)
      """, version.id(), version.floorId(), version.number(), version.file().name(),
      version.file().format().mediaType(), bytes, Timestamp.from(version.createdAt()));
  }

  @Override
  public Optional<Original> findOriginal(UUID siteId, UUID floorId, UUID versionId) {
    return jdbc.query("""
      SELECT v.version_number, v.file_name, v.media_type, v.file_bytes, v.created_at
      FROM planning_floor_plan_file_versions v
      JOIN planning_floor_sites f ON f.floor_id = v.floor_id
      WHERE f.site_id = ? AND v.floor_id = ? AND v.id = ?
      """, (rs, row) -> {
        byte[] bytes = rs.getBytes("file_bytes");
        var format = FloorPlanFile.Format.fromMediaType(rs.getString("media_type"));
        var file = new FloorPlanFile(rs.getString("file_name"), format, bytes.length);
        return new Original(new FloorPlanVersion(versionId, siteId, floorId,
          rs.getInt("version_number"), file, rs.getTimestamp("created_at").toInstant()), bytes);
      }, siteId, floorId, versionId).stream().findFirst();
  }
}
