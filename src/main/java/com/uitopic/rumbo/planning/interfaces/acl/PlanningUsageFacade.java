package com.uitopic.rumbo.planning.interfaces.acl;

import java.util.UUID;

public interface PlanningUsageFacade {
  PlanningUsage usageOf(UUID ownerUserId);
}
