package com.uitopic.rumbo.planning.interfaces.acl;

import java.util.List;
import java.util.UUID;

/** Public Planning contract used by other contexts to authorize access to a clinic site and list its sites. */
public interface PlanningSiteOwnershipFacade {
  boolean isOwnedBy(UUID siteId, UUID userId);

  /** Sites of the account, ordered by name. */
  List<OwnedSite> sitesOwnedBy(UUID userId);

  record OwnedSite(UUID id, String name) { }
}
