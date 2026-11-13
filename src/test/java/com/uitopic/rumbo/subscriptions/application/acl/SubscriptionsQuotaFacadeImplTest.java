package com.uitopic.rumbo.subscriptions.application.acl;

import com.uitopic.rumbo.subscriptions.application.ports.PlanQuotaCatalog;
import com.uitopic.rumbo.subscriptions.domain.model.aggregates.Subscription;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.*;
import com.uitopic.rumbo.subscriptions.domain.repositories.SubscriptionRepository;
import com.uitopic.rumbo.subscriptions.interfaces.acl.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubscriptionsQuotaFacadeImplTest {
  private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
  private final UUID owner = UUID.randomUUID();
  private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
  private final PlanQuotaCatalog quotas = mock(PlanQuotaCatalog.class);
  private final SubscriptionsQuotaFacade facade = new SubscriptionsQuotaFacadeImpl(
      subscriptions, quotas, Clock.fixed(NOW, ZoneOffset.UTC));

  private Subscription given(SubscriptionStatus status, Instant end, PlanCode plan) {
    var subscription = new Subscription(UUID.randomUUID(), owner, plan, status,
        NOW.minusSeconds(100), end, null, NOW, NOW);
    when(subscriptions.findByOwnerUserId(owner)).thenReturn(Optional.of(subscription));
    return subscription;
  }

  @Test void requiresAnActiveSubscriptionWithoutConsultingQuotas() {
    when(subscriptions.findByOwnerUserId(owner)).thenReturn(Optional.empty());
    assertThat(facade.checkCanRegister(owner, QuotaResource.SITE, 0).status())
        .isEqualTo(QuotaDecision.Status.SUBSCRIPTION_REQUIRED);
    for (var status : SubscriptionStatus.values()) {
      given(status, status == SubscriptionStatus.ACTIVE ? NOW.minusNanos(1) : NOW.plusSeconds(100), PlanCode.BASIC);
      assertThat(facade.checkCanRegister(owner, QuotaResource.BEACON, 0).status())
          .isEqualTo(QuotaDecision.Status.SUBSCRIPTION_REQUIRED);
    }
    verifyNoInteractions(quotas);
  }

  @Test void translatesEveryResourceAndReturnsItsLimitAtTheBoundary() {
    given(SubscriptionStatus.ACTIVE, NOW.plusSeconds(1), PlanCode.BASIC);
    when(quotas.quotaFor(PlanCode.BASIC)).thenReturn(new PlanQuota(1, 3, 15));
    for (var resource : QuotaResource.values()) {
      int limit = switch (resource) { case SITE -> 1; case FLOOR -> 3; case BEACON -> 15; };
      assertThat(facade.checkCanRegister(owner, resource, limit - 1).status()).isEqualTo(QuotaDecision.Status.ALLOWED);
      assertThat(facade.checkCanRegister(owner, resource, limit))
          .isEqualTo(new QuotaDecision(QuotaDecision.Status.QUOTA_EXCEEDED, limit));
    }
  }

  @Test void preservesTheQueryRuleAtTheExactPeriodEnd() {
    var subscription = given(SubscriptionStatus.ACTIVE, NOW, PlanCode.ENTERPRISE);
    when(quotas.quotaFor(PlanCode.ENTERPRISE)).thenReturn(new PlanQuota(null, null, null));
    assertThat(subscription.isActiveAt(NOW)).isTrue();
    assertThat(subscription.isActiveAt(NOW.plusNanos(1))).isFalse();
    for (var resource : QuotaResource.values()) {
      assertThat(facade.checkCanRegister(owner, resource, Long.MAX_VALUE).status())
          .isEqualTo(QuotaDecision.Status.ALLOWED);
    }
  }
}
