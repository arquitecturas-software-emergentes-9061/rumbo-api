package com.uitopic.rumbo.subscriptions.application;

import com.uitopic.rumbo.subscriptions.application.queryhandlers.*;
import com.uitopic.rumbo.subscriptions.application.ports.*;
import com.uitopic.rumbo.subscriptions.domain.exceptions.SubscriptionNotFoundException;
import com.uitopic.rumbo.subscriptions.domain.model.aggregates.*;
import com.uitopic.rumbo.subscriptions.domain.model.queries.GetMySubscriptionQuery;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.*;
import com.uitopic.rumbo.subscriptions.domain.repositories.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetMySubscriptionQueryHandlerTest {
  private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
  private final UUID owner = UUID.randomUUID();
  private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
  private final PlanRepository plans = mock(PlanRepository.class);
  private final PlanQuotaCatalog quotas = code -> new PlanQuota(1, 3, 15);
  private final ResourceUsageDirectory usage = mock(ResourceUsageDirectory.class);
  private final GetMySubscriptionQueryHandler handler = new GetMySubscriptionQueryHandler(
      subscriptions, plans, Clock.fixed(NOW, ZoneOffset.UTC), quotas, usage);
  private final Plan plan = new Plan(UUID.randomUUID(), PlanCode.BASIC, "Básico",
      new Money(new BigDecimal("39.90"), "PEN"), BillingInterval.MONTH, PurchaseMode.CHECKOUT, 1, false);

  private Subscription given(Instant end, SubscriptionStatus status) {
    var start = NOW.minus(Duration.ofDays(31));
    var subscription = new Subscription(UUID.randomUUID(), owner, PlanCode.BASIC, status,
        start, end, "sub_private", start, start);
    when(subscriptions.findByOwnerUserId(owner)).thenReturn(Optional.of(subscription));
    when(plans.findByCode(PlanCode.BASIC)).thenReturn(Optional.of(plan));
    when(usage.usageOf(owner)).thenReturn(new ResourceUsageDirectory.ResourceUsage(1, 2, 4));
    return subscription;
  }

  @Test void returnsActiveSubscriptionAndItsPlanEvenIfPlanIsNoLongerSold() {
    var subscription = given(NOW.plus(Duration.ofDays(30)), SubscriptionStatus.ACTIVE);
    var view = handler.handle(new GetMySubscriptionQuery(owner));
    assertThat(view.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    assertThat(view.plan()).isEqualTo(plan);
    assertThat(view.currentPeriodStart()).isEqualTo(subscription.currentPeriodStart());
    assertThat(view.currentPeriodEnd()).isEqualTo(subscription.currentPeriodEnd());
    assertThat(view.daysRemaining()).isEqualTo(30);
    assertThat(view.quota()).isEqualTo(new PlanQuota(1, 3, 15));
    assertThat(view.usage()).isEqualTo(new UsageView(new UsageView.ResourceUsageView(1, 1),
        new UsageView.ResourceUsageView(2, 3), new UsageView.ResourceUsageView(4, 15)));
    verify(subscriptions, never()).save(any());
  }

  @Test void reportsExpiredWithoutChangingTheAggregateOrPersistence() {
    var subscription = given(NOW.minusSeconds(1), SubscriptionStatus.ACTIVE);
    var view = handler.handle(new GetMySubscriptionQuery(owner));
    assertThat(view.status()).isEqualTo(SubscriptionStatus.EXPIRED);
    assertThat(view.daysRemaining()).isZero();
    assertThat(view.usage().beacons()).isEqualTo(new UsageView.ResourceUsageView(4, 15));
    assertThat(subscription.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    verify(subscriptions, never()).save(any());
  }

  @Test void countsOnlyCompleteDaysAndClampsToZeroAtTheBoundary() {
    for (long seconds : new long[]{86401, 86400, 86399, 1, 0, -1, -86400}) {
      given(NOW.plusSeconds(seconds), SubscriptionStatus.ACTIVE);
      var view = handler.handle(new GetMySubscriptionQuery(owner));
      assertThat(view.daysRemaining()).isEqualTo(Math.max(0, seconds / 86400));
      assertThat(view.status()).isEqualTo(seconds < 0 ? SubscriptionStatus.EXPIRED : SubscriptionStatus.ACTIVE);
    }
  }

  @Test void preservesCancelledStatusAfterThePeriodEnds() {
    given(NOW.minusSeconds(1), SubscriptionStatus.CANCELLED);
    assertThat(handler.handle(new GetMySubscriptionQuery(owner)).status()).isEqualTo(SubscriptionStatus.CANCELLED);
  }

  @Test void missingSubscriptionThrowsWithoutReadingPlans() {
    when(subscriptions.findByOwnerUserId(owner)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> handler.handle(new GetMySubscriptionQuery(owner)))
        .isInstanceOf(SubscriptionNotFoundException.class).hasMessage("SUBSCRIPTION_NOT_FOUND");
    verifyNoInteractions(plans);
  }
}
