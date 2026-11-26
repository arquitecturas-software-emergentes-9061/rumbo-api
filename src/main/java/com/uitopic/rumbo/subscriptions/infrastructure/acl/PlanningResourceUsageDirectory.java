package com.uitopic.rumbo.subscriptions.infrastructure.acl;

import com.uitopic.rumbo.planning.interfaces.acl.PlanningUsageFacade;
import com.uitopic.rumbo.subscriptions.application.ports.ResourceUsageDirectory;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PlanningResourceUsageDirectory implements ResourceUsageDirectory {
  private final PlanningUsageFacade planning;

  public PlanningResourceUsageDirectory(PlanningUsageFacade planning) { this.planning = planning; }

  @Override
  public ResourceUsage usageOf(UUID ownerUserId) {
    var usage = planning.usageOf(ownerUserId);
    return new ResourceUsage(usage.sites(), usage.floors(), usage.beacons());
  }
}
