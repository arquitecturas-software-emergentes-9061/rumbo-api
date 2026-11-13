package com.uitopic.rumbo.planning.application.internal.outboundservices;

import java.util.UUID;

public interface PlanningResourceUsage {
  void lockOwner(UUID owner);
  long sites(UUID owner);
  long floors(UUID owner);
  long beacons(UUID owner);
}
