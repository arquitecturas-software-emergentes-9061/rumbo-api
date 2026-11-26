package com.uitopic.rumbo.planning.application.internal.commandservices;

import com.uitopic.rumbo.planning.domain.exceptions.ClinicStructureConflictException;
import com.uitopic.rumbo.planning.application.internal.outboundservices.PlanningResourceUsage;
import com.uitopic.rumbo.subscriptions.interfaces.acl.QuotaResource;
import com.uitopic.rumbo.subscriptions.interfaces.acl.SubscriptionsQuotaFacade;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
class PlanningQuotaGuard {
  private final PlanningResourceUsage usage;
  private final SubscriptionsQuotaFacade subscriptions;

  PlanningQuotaGuard(PlanningResourceUsage usage, SubscriptionsQuotaFacade subscriptions) {
    this.usage = usage;
    this.subscriptions = subscriptions;
  }

  void check(UUID owner, QuotaResource kind) {
    usage.lockOwner(owner);
    long used = switch (kind) {
      case SITE -> usage.sites(owner);
      case FLOOR -> usage.floors(owner);
      case BEACON -> usage.beacons(owner);
    };
    var decision = subscriptions.checkCanRegister(owner, kind, used);
    switch (decision.status()) {
      case ALLOWED -> { }
      case SUBSCRIPTION_REQUIRED -> throw new ClinicStructureConflictException("SUBSCRIPTION_REQUIRED",
        "An active subscription is required to register sites, floors or beacons");
      case QUOTA_EXCEEDED -> throw new ClinicStructureConflictException("PLAN_QUOTA_EXCEEDED",
        "Your plan allows at most " + decision.limit() + " " + kind.name().toLowerCase(Locale.ROOT) + "s",
        decision.limit());
    }
  }
}
