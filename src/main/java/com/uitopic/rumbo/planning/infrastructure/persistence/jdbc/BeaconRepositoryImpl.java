package com.uitopic.rumbo.planning.infrastructure.persistence.jdbc;

import com.uitopic.rumbo.planning.application.internal.exceptions.DuplicateBeaconIdentifierException;
import com.uitopic.rumbo.planning.domain.model.entities.Beacon;
import com.uitopic.rumbo.planning.domain.repositories.BeaconRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC inventory adapter; the database enforces unique identifiers per site. */
@Repository
public class BeaconRepositoryImpl implements BeaconRepository {
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public BeaconRepositoryImpl(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  public Beacon create(Beacon beacon) {
    Timestamp now = Timestamp.from(Instant.now(clock));
    try {
      jdbc.update("""
          INSERT INTO positioning_beacons
              (id, site_id, floor_id, identifier, x, y, active, name, proximity_uuid, major,
               minor, tx_power_dbm, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """, beacon.id(), beacon.siteId(), beacon.floorId(), beacon.identifier(),
          beacon.x(), beacon.y(), beacon.active(), beacon.name(), beacon.proximityUuid(),
          beacon.major(), beacon.minor(), beacon.txPowerDbm(), now, now);
    } catch (DataIntegrityViolationException error) {
      if (error.getMostSpecificCause() instanceof SQLException sql
          && "23505".equals(sql.getSQLState())) throw new DuplicateBeaconIdentifierException();
      throw error;
    }
    return beacon;
  }

  @Override
  public List<Beacon> findBySite(UUID siteId, UUID floorId) {
    if (floorId == null) {
      return jdbc.query("""
          SELECT id, site_id, floor_id, identifier, x, y, active, name, proximity_uuid, major, minor, tx_power_dbm FROM positioning_beacons
          WHERE site_id = ? ORDER BY identifier
          """, BeaconRepositoryImpl::map, siteId);
    }
    return jdbc.query("""
        SELECT id, site_id, floor_id, identifier, x, y, active, name, proximity_uuid, major, minor, tx_power_dbm FROM positioning_beacons
        WHERE site_id = ? AND floor_id = ? ORDER BY identifier
        """, BeaconRepositoryImpl::map, siteId, floorId);
  }

  @Override
  public Optional<Beacon> findBySiteAndId(UUID siteId, UUID beaconId) {
    return jdbc.query("""
        SELECT id, site_id, floor_id, identifier, x, y, active, name, proximity_uuid, major, minor, tx_power_dbm FROM positioning_beacons
        WHERE site_id = ? AND id = ?
        """, BeaconRepositoryImpl::map, siteId, beaconId).stream().findFirst();
  }

  @Override
  public boolean update(Beacon beacon) {
    try {
      return jdbc.update("""
          UPDATE positioning_beacons
          SET floor_id = ?, identifier = ?, x = ?, y = ?, active = ?, name = ?,
              proximity_uuid = ?, major = ?, minor = ?, tx_power_dbm = ?, updated_at = ?
          WHERE site_id = ? AND id = ?
          """, beacon.floorId(), beacon.identifier(), beacon.x(), beacon.y(), beacon.active(),
          beacon.name(), beacon.proximityUuid(), beacon.major(), beacon.minor(),
          beacon.txPowerDbm(), Timestamp.from(Instant.now(clock)), beacon.siteId(),
          beacon.id()) == 1;
    } catch (DataIntegrityViolationException error) {
      if (error.getMostSpecificCause() instanceof SQLException sql
          && "23505".equals(sql.getSQLState())) throw new DuplicateBeaconIdentifierException();
      throw error;
    }
  }

  @Override
  public boolean updateActive(UUID siteId, UUID beaconId, boolean active) {
    return jdbc.update("""
        UPDATE positioning_beacons SET active = ?, updated_at = ?
        WHERE site_id = ? AND id = ?
        """, active, Timestamp.from(Instant.now(clock)), siteId, beaconId) == 1;
  }

  private static Beacon map(ResultSet rs, int ignored) throws SQLException {
    return new Beacon(rs.getObject("id", UUID.class), rs.getObject("site_id", UUID.class),
        rs.getObject("floor_id", UUID.class), rs.getString("identifier"),
        rs.getObject("x", Double.class), rs.getObject("y", Double.class), rs.getBoolean("active"),
        rs.getString("name"), rs.getObject("proximity_uuid", UUID.class),
        rs.getObject("major", Integer.class), rs.getObject("minor", Integer.class),
        rs.getObject("tx_power_dbm", Integer.class));
  }
}
