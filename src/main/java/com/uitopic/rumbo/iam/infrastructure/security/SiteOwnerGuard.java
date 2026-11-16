package com.uitopic.rumbo.iam.infrastructure.security;

import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import com.uitopic.rumbo.planning.interfaces.acl.PlanningSiteOwnershipFacade;
import org.springframework.context.annotation.Profile;
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
  private final PlanningSiteOwnershipFacade sites;
  public SiteOwnerGuard(PlanningSiteOwnershipFacade sites) { this.sites = sites; }

  public boolean isOwner(Authentication authentication, UUID siteId) {
    if (siteId == null || authentication == null || !authentication.isAuthenticated()
      || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal caller)) return false;
    return sites.isOwnedBy(siteId, caller.userId());
  }
}
