package com.uitopic.rumbo.subscriptions.domain.model.valueobjects;

public record PlanQuota(Integer sites, Integer floors, Integer beacons) {
  public PlanQuota {
    if ((sites != null && sites < 0) || (floors != null && floors < 0) || (beacons != null && beacons < 0)) {
      throw new IllegalArgumentException("Plan quotas must be nonnegative");
    }
  }

  public Integer limit(ResourceKind kind) {
    return switch (kind) {
      case SITE -> sites;
      case FLOOR -> floors;
      case BEACON -> beacons;
    };
  }

  public boolean allows(ResourceKind kind, long currentUsage) {
    Integer limit = limit(kind);
    return limit == null || currentUsage < limit;
  }
}
