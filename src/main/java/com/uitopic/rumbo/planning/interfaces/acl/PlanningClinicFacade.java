package com.uitopic.rumbo.planning.interfaces.acl;

import java.util.Optional;
import java.util.UUID;

public interface PlanningClinicFacade {
  Optional<ClinicSummary> findClinicOwnedBy(UUID ownerUserId);
  record ClinicSummary(UUID id, String name) {}
}
