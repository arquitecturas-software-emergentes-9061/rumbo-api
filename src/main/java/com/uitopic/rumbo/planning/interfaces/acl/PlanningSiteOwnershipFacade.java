package com.uitopic.rumbo.planning.interfaces.acl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public Planning contract used by other contexts to authorize access to a clinic site and list its sites. */
public interface PlanningSiteOwnershipFacade {
  boolean isOwnedBy(UUID siteId, UUID userId);

  /** Account that administers the site, empty if the site does not exist. */
  Optional<UUID> ownerOf(UUID siteId);

  /** Sites of the account, ordered by name. */
  List<OwnedSite> sitesOwnedBy(UUID userId);

  record OwnedSite(UUID id, String name) { }
}
