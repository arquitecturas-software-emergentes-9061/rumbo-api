package com.uitopic.rumbo.planning.infrastructure.persistence.jdbc;

import com.uitopic.rumbo.planning.application.internal.outboundservices.PlanningResourceUsage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class PlanningResourceUsageReader implements PlanningResourceUsage {
  private final JdbcTemplate jdbc;

  public PlanningResourceUsageReader(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void lockOwner(UUID owner) {
    // Transaction-scoped lock serializes registrations for the same clinic across application instances.
    jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 93))", rs -> {}, owner.toString());
  }

  @Override
  public long sites(UUID owner) {
    return jdbc.queryForObject("SELECT count(*) FROM indoor_sites WHERE owner_user_id = ?", Long.class, owner);
  }

  @Override
  public long floors(UUID owner) {
    return jdbc.queryForObject("""
      SELECT count(*) FROM planning_floors f JOIN indoor_sites s ON s.id = f.site_id
      WHERE s.owner_user_id = ?
      """, Long.class, owner);
  }

  @Override
  public long beacons(UUID owner) {
    return jdbc.queryForObject("""
      SELECT count(*) FROM positioning_beacons b JOIN indoor_sites s ON s.id = b.site_id
      WHERE s.owner_user_id = ?
      """, Long.class, owner);
  }
}
