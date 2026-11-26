package com.uitopic.rumbo.subscriptions.application.ports;

import java.util.UUID;

public interface ResourceUsageDirectory {
  ResourceUsage usageOf(UUID ownerUserId);

  record ResourceUsage(long sites, long floors, long beacons) {}
}
