package com.uitopic.rumbo.positioning.application;

import com.uitopic.rumbo.positioning.domain.Beacon;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Positioning inventory. Commands are authenticated and account-scoped:
 * a site's owner must match the caller for reads as well as writes.
 * This deliberately does not claim a floor is calibrated or a beacon is transmitting.
 */
@Service
public class BeaconInventoryService {
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public BeaconInventoryService(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = Objects.requireNonNull(jdbc);
    this.clock = Objects.requireNonNull(clock);
  }

  public record Site(UUID id, String name) {}
  public static final class NotFound extends RuntimeException {
    public NotFound() { super("Site or beacon not found"); }
  }
  public static final class DuplicateIdentifier extends RuntimeException {
    public DuplicateIdentifier() { super("The beacon identifier is already registered in this site"); }
  }

  @Transactional
  public Site createSite(UUID ownerUserId, String rawName) {
    requireUser(ownerUserId);
    String name = cleanName(rawName);
    Site site = new Site(UUID.randomUUID(), name);
    jdbc.update("INSERT INTO indoor_sites (id, owner_user_id, name, created_at) VALUES (?, ?, ?, ?)",
        site.id(), ownerUserId, name, java.sql.Timestamp.from(Instant.now(clock)));
    return site;
  }

  @Transactional(readOnly = true)
  public List<Site> listSites(UUID ownerUserId) {
    requireUser(ownerUserId);
    return jdbc.query("SELECT id, name FROM indoor_sites WHERE owner_user_id = ? ORDER BY name, id",
        (rs, index) -> new Site(rs.getObject("id", UUID.class), rs.getString("name")), ownerUserId);
  }

  @Transactional
  public Beacon register(UUID ownerUserId, UUID siteId, UUID floorId,
                         String rawIdentifier, double x, double y) {
    assertOwner(ownerUserId, siteId);
    Beacon beacon = new Beacon(UUID.randomUUID(), siteId, floorId, rawIdentifier, x, y, true);
    try {
      jdbc.update("""
          INSERT INTO positioning_beacons
              (id, site_id, floor_id, identifier, x, y, active, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, true, ?, ?)
          """, beacon.id(), beacon.siteId(), beacon.floorId(), beacon.identifier(),
          beacon.x(), beacon.y(), java.sql.Timestamp.from(Instant.now(clock)), java.sql.Timestamp.from(Instant.now(clock)));
    } catch (DataIntegrityViolationException error) {
      // A FK or CHECK violation is not an identifier conflict.
      if (error.getMostSpecificCause() instanceof SQLException sql
          && "23505".equals(sql.getSQLState())) {
        throw new DuplicateIdentifier();
      }
      throw error;
    }
    return beacon;
  }

  @Transactional(readOnly = true)
  public List<Beacon> list(UUID ownerUserId, UUID siteId, UUID floorId) {
    assertOwner(ownerUserId, siteId);
    if (floorId == null) {
      return jdbc.query("""
          SELECT id, site_id, floor_id, identifier, x, y, active FROM positioning_beacons
          WHERE site_id = ? ORDER BY identifier
          """, BeaconInventoryService::mapBeacon, siteId);
    }
    return jdbc.query("""
        SELECT id, site_id, floor_id, identifier, x, y, active FROM positioning_beacons
        WHERE site_id = ? AND floor_id = ? ORDER BY identifier
        """, BeaconInventoryService::mapBeacon, siteId, floorId);
  }

  @Transactional(readOnly = true)
  public Beacon get(UUID ownerUserId, UUID siteId, UUID beaconId) {
    assertOwner(ownerUserId, siteId);
    if (beaconId == null) throw new NotFound();
    return jdbc.query("""
        SELECT id, site_id, floor_id, identifier, x, y, active FROM positioning_beacons
        WHERE site_id = ? AND id = ?
        """, BeaconInventoryService::mapBeacon, siteId, beaconId)
      .stream().findFirst().orElseThrow(NotFound::new);
  }

  @Transactional
  public Beacon setActive(UUID ownerUserId, UUID siteId, UUID beaconId, boolean active) {
    assertOwner(ownerUserId, siteId);
    int changed = jdbc.update("""
        UPDATE positioning_beacons SET active = ?, updated_at = ?
        WHERE site_id = ? AND id = ?
        """, active, java.sql.Timestamp.from(Instant.now(clock)), siteId, beaconId);
    if (changed == 0) throw new NotFound();
    return get(ownerUserId, siteId, beaconId);
  }

  private void assertOwner(UUID userId, UUID siteId) {
    requireUser(userId);
    if (siteId == null) throw new NotFound();
    Integer count = jdbc.queryForObject(
      "SELECT count(*) FROM indoor_sites WHERE id = ? AND owner_user_id = ?",
      Integer.class, siteId, userId);
    if (count == null || count == 0) throw new NotFound();
  }

  private static void requireUser(UUID userId) {
    if (userId == null) throw new IllegalArgumentException("Authenticated user required");
  }

  private static String cleanName(String raw) {
    if (raw == null) throw new IllegalArgumentException("Site name is required");
    String name = raw.strip();
    if (name.length() < 2 || name.length() > 120)
      throw new IllegalArgumentException("Site name must contain 2-120 characters");
    return name;
  }

  private static Beacon mapBeacon(ResultSet rs, int index) throws SQLException {
    return new Beacon(rs.getObject("id", UUID.class), rs.getObject("site_id", UUID.class),
      rs.getObject("floor_id", UUID.class), rs.getString("identifier"),
      rs.getDouble("x"), rs.getDouble("y"), rs.getBoolean("active"));
  }
}
