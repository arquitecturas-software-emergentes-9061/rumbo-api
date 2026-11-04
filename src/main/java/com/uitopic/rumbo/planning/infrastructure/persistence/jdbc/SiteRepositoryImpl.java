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
    jdbc.update("INSERT INTO indoor_sites (id, owner_user_id, name, created_at) VALUES (?, ?, ?, ?)",
        site.id(), ownerUserId, site.name(), Timestamp.from(Instant.now(clock)));
    return site;
  }

  @Override
  public List<Site> findByOwner(UUID ownerUserId) {
    return jdbc.query("SELECT id, name FROM indoor_sites WHERE owner_user_id = ? ORDER BY name, id",
        (rs, index) -> new Site(rs.getObject("id", UUID.class), rs.getString("name")), ownerUserId);
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
