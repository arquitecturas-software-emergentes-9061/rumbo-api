package com.uitopic.rumbo.planning.application.internal.commandservices;

import com.uitopic.rumbo.planning.domain.exceptions.ClinicStructureConflictException;
import com.uitopic.rumbo.planning.application.internal.outboundservices.PlanningResourceUsage;
import com.uitopic.rumbo.subscriptions.interfaces.acl.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlanningQuotaGuardTest {
  private final UUID owner = UUID.randomUUID();
  private final PlanningResourceUsage usage = mock(PlanningResourceUsage.class);
  private final SubscriptionsQuotaFacade subscriptions = mock(SubscriptionsQuotaFacade.class);
  private final PlanningQuotaGuard guard = new PlanningQuotaGuard(usage, subscriptions);

  @Test void locksBeforeCountingAndPassesTheOwnersUsageForEachResource() {
    when(usage.sites(owner)).thenReturn(2L);
    when(usage.floors(owner)).thenReturn(7L);
    when(usage.beacons(owner)).thenReturn(20L);
    for (var resource : QuotaResource.values()) {
      long used = switch (resource) { case SITE -> 2; case FLOOR -> 7; case BEACON -> 20; };
      when(subscriptions.checkCanRegister(owner, resource, used))
          .thenReturn(new QuotaDecision(QuotaDecision.Status.ALLOWED, null));
      guard.check(owner, resource);
      var order = inOrder(usage, subscriptions);
      order.verify(usage).lockOwner(owner);
      switch (resource) {
        case SITE -> order.verify(usage).sites(owner);
        case FLOOR -> order.verify(usage).floors(owner);
        case BEACON -> order.verify(usage).beacons(owner);
      }
      order.verify(subscriptions).checkCanRegister(owner, resource, used);
      clearInvocations(usage, subscriptions);
    }
  }

  @Test void mapsBothRejectionsToPlanningConflictsWithThePublicCodeAndLimit() {
    when(subscriptions.checkCanRegister(owner, QuotaResource.SITE, 0))
        .thenReturn(new QuotaDecision(QuotaDecision.Status.SUBSCRIPTION_REQUIRED, null));
    assertThatThrownBy(() -> guard.check(owner, QuotaResource.SITE))
        .isInstanceOfSatisfying(ClinicStructureConflictException.class, error -> {
          assertThat(error.code()).isEqualTo("SUBSCRIPTION_REQUIRED");
          assertThat(error.limit()).isNull();
        }).hasMessage("An active subscription is required to register sites, floors or beacons");
    when(subscriptions.checkCanRegister(owner, QuotaResource.SITE, 0))
        .thenReturn(new QuotaDecision(QuotaDecision.Status.QUOTA_EXCEEDED, 0));
    assertThatThrownBy(() -> guard.check(owner, QuotaResource.SITE))
        .isInstanceOfSatisfying(ClinicStructureConflictException.class, error -> {
          assertThat(error.code()).isEqualTo("PLAN_QUOTA_EXCEEDED");
          assertThat(error.limit()).isZero();
        }).hasMessage("Your plan allows at most 0 sites");
  }
}
