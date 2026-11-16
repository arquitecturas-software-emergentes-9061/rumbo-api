package com.uitopic.rumbo.planning.application.acl;

import com.uitopic.rumbo.planning.domain.repositories.SiteRepository;
import com.uitopic.rumbo.planning.interfaces.acl.PlanningSiteOwnershipFacade;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.util.UUID;

/** Exposes site ownership without sharing Planning's persistence model. */
@Service
@Profile("postgres")
public class PlanningSiteOwnershipFacadeImpl implements PlanningSiteOwnershipFacade {
  private final SiteRepository sites;

  public PlanningSiteOwnershipFacadeImpl(SiteRepository sites) { this.sites = sites; }

  @Override
  public boolean isOwnedBy(UUID siteId, UUID userId) {
    return sites.isOwnedBy(siteId, userId);
  }
}
