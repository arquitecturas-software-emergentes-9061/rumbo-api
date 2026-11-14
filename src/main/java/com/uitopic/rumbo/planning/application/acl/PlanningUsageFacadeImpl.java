package com.uitopic.rumbo.planning.application.acl;

import com.uitopic.rumbo.planning.application.internal.outboundservices.PlanningResourceUsage;
import com.uitopic.rumbo.planning.interfaces.acl.PlanningUsage;
import com.uitopic.rumbo.planning.interfaces.acl.PlanningUsageFacade;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanningUsageFacadeImpl implements PlanningUsageFacade {
  private final PlanningResourceUsage usage;

  public PlanningUsageFacadeImpl(PlanningResourceUsage usage) { this.usage = usage; }

  @Override
  @Transactional(readOnly = true)
  public PlanningUsage usageOf(UUID ownerUserId) {
    return new PlanningUsage(usage.sites(ownerUserId), usage.floors(ownerUserId), usage.beacons(ownerUserId));
  }
}
