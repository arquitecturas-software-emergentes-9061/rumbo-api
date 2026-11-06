package com.uitopic.rumbo.planning.infrastructure.persistence.jdbc;

import com.uitopic.rumbo.planning.domain.model.entities.Site;
import com.uitopic.rumbo.planning.domain.repositories.SiteRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC persistence for Planning sites and account ownership. */
@Repository
public class SiteRepositoryImpl implements SiteRepository {
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public SiteRepositoryImpl(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  public Site create(UUID ownerUserId, Site site) {
    jdbc.update("INSERT INTO indoor_sites (id, owner_user_id, name, address, clinic_id, created_at) VALUES (?, ?, ?, ?, ?, ?)",
        site.id(), ownerUserId, site.name(), site.address(), site.clinicId(), Timestamp.from(Instant.now(clock)));
    return site;
  }

  @Override
  public List<Site> findByOwner(UUID ownerUserId) {
    return jdbc.query("SELECT id, name, address, clinic_id FROM indoor_sites WHERE owner_user_id = ? ORDER BY name, id",
        (rs, index) -> new Site(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("address"),
            rs.getObject("clinic_id", UUID.class)), ownerUserId);
  }

  @Override
  public Site update(UUID ownerUserId, Site site) {
    int changed = jdbc.update("UPDATE indoor_sites SET name=?, address=? WHERE id=? AND owner_user_id=?",
        site.name(), site.address(), site.id(), ownerUserId);
    if (changed != 1) throw new com.uitopic.rumbo.planning.application.internal.exceptions.PlanningSiteNotFoundException();
    return site;
  }

  @Override
  public boolean isOwnedBy(UUID siteId, UUID ownerUserId) {
    if (siteId == null || ownerUserId == null) return false;
    Integer count = jdbc.queryForObject(
        "SELECT count(*) FROM indoor_sites WHERE id = ? AND owner_user_id = ?",
        Integer.class, siteId, ownerUserId);
    return count != null && count == 1;
  }
}
