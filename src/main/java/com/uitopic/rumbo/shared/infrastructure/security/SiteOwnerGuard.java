package com.uitopic.rumbo.shared.infrastructure.security;

import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.util.UUID;

/**
 * Temporary account-owner authorization until shared clinic membership is implemented.
 * Never equates a user-supplied siteId with authorization.
 */
@Component("siteOwnerGuard")
@Profile("postgres")
public class SiteOwnerGuard {
  private final JdbcTemplate jdbc;
  public SiteOwnerGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public boolean isOwner(Authentication authentication, UUID siteId) {
    if (siteId == null || authentication == null || !authentication.isAuthenticated()
      || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal caller)) return false;
    Integer count = jdbc.queryForObject(
      "SELECT count(*) FROM indoor_sites WHERE id = ? AND owner_user_id = ?",
      Integer.class, siteId, caller.userId());
    return count != null && count == 1;
  }
}
