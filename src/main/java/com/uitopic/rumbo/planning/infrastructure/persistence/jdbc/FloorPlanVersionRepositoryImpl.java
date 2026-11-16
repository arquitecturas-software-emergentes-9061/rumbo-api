package com.uitopic.rumbo.planning.infrastructure.persistence.jdbc;

import com.uitopic.rumbo.planning.domain.model.entities.FloorPlanVersion;
import com.uitopic.rumbo.planning.domain.model.valueobjects.FloorPlanFile;
import com.uitopic.rumbo.planning.domain.repositories.FloorPlanVersionRepository;
import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
  public Optional<List<FloorPlanVersion>> findVersions(UUID siteId, UUID floorId) {
    return jdbc.query("""
      SELECT v.id, v.version_number, v.file_name, v.media_type, octet_length(v.file_bytes) AS size_bytes,
        v.created_at
      FROM planning_floor_sites f
      LEFT JOIN planning_floor_plan_file_versions v ON v.floor_id = f.floor_id
      WHERE f.site_id = ? AND f.floor_id = ?
      ORDER BY v.version_number DESC
      """, rs -> {
        if (!rs.next()) return Optional.empty();
        var versions = new ArrayList<FloorPlanVersion>();
        do {
          UUID id = rs.getObject("id", UUID.class);
          if (id != null) versions.add(version(rs, id, siteId, floorId, rs.getLong("size_bytes")));
        } while (rs.next());
        return Optional.of(List.copyOf(versions));
      }, siteId, floorId);
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
        return new Original(version(rs, versionId, siteId, floorId, bytes.length), bytes);
      }, siteId, floorId, versionId).stream().findFirst();
  }

  private static FloorPlanVersion version(ResultSet rs, UUID id, UUID siteId, UUID floorId,
      long sizeBytes) throws SQLException {
    var format = FloorPlanFile.Format.fromMediaType(rs.getString("media_type"));
    var file = new FloorPlanFile(rs.getString("file_name"), format, sizeBytes);
    return new FloorPlanVersion(id, siteId, floorId, rs.getInt("version_number"), file,
      rs.getTimestamp("created_at").toInstant());
  }
}
