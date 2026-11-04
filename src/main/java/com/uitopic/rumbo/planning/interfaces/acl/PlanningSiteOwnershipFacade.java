package com.uitopic.rumbo.planning.interfaces.acl;

import java.util.UUID;

/** Public Planning contract used by IAM to authorize access to a clinic site. */
public interface PlanningSiteOwnershipFacade {
  boolean isOwnedBy(UUID siteId, UUID userId);
}
