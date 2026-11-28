package com.uitopic.rumbo.communications.infrastructure.acl;

import com.uitopic.rumbo.communications.application.ports.RecipientDirectory;
import com.uitopic.rumbo.communications.application.ports.SiteAdministrators;
import com.uitopic.rumbo.planning.interfaces.acl.PlanningSiteOwnershipFacade;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Administrators of a site as Planning and IAM know them (WI-US32-04, WI-US32-06). Until clinics share a membership,
 * the administrator of a site is its owner. The owner is checked again against the site, and only an enabled
 * {@code CLINIC_ADMIN} account is returned, so a site never notifies an account of another organization.
 */
@Component
@Profile("postgres")
public class PlanningSiteAdministrators implements SiteAdministrators {
  private final PlanningSiteOwnershipFacade planning;
  private final RecipientDirectory directory;

  public PlanningSiteAdministrators(PlanningSiteOwnershipFacade planning, RecipientDirectory directory) {
    this.planning = planning;
    this.directory = directory;
  }

  @Override
  public List<RecipientDirectory.Contact> of(UUID siteId) {
    return planning.ownerOf(siteId)
        .filter(owner -> planning.isOwnedBy(siteId, owner))
        .flatMap(directory::contactOf)
        .filter(contact -> contact.reachable() && contact.isAdministrator())
        .map(List::of)
        .orElse(List.of());
  }
}
