package com.uitopic.rumbo.analytics.infrastructure.acl;

import com.uitopic.rumbo.analytics.application.ports.AdministeredSites;
import com.uitopic.rumbo.planning.interfaces.acl.PlanningSiteOwnershipFacade;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Reads the sites of an administrator through the public contract of Planning. */
@Component
@Profile("postgres")
public class PlanningAdministeredSites implements AdministeredSites {
  private final PlanningSiteOwnershipFacade planning;

  public PlanningAdministeredSites(PlanningSiteOwnershipFacade planning) {
    this.planning = planning;
  }

  @Override
  public List<Site> of(UUID administratorUserId) {
    return planning.sitesOwnedBy(administratorUserId).stream().map(site -> new Site(site.id(), site.name())).toList();
  }
}
