package com.uitopic.rumbo.subscriptions.application.queryhandlers;

import com.uitopic.rumbo.subscriptions.domain.exceptions.*;
import com.uitopic.rumbo.subscriptions.domain.model.queries.GetMySubscriptionQuery;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.SubscriptionStatus;
import com.uitopic.rumbo.subscriptions.domain.repositories.*;
import java.time.Clock;
import java.time.Duration;
import com.uitopic.rumbo.subscriptions.application.ports.PlanQuotaCatalog;
import com.uitopic.rumbo.subscriptions.application.ports.ResourceUsageDirectory;
import com.uitopic.rumbo.subscriptions.application.queryhandlers.UsageView.ResourceUsageView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetMySubscriptionQueryHandler {
  private final SubscriptionRepository subscriptions;
  private final PlanRepository plans;
  private final Clock clock;
  private final PlanQuotaCatalog quotas;
  private final ResourceUsageDirectory usage;

  public GetMySubscriptionQueryHandler(SubscriptionRepository subscriptions, PlanRepository plans, Clock clock,
      PlanQuotaCatalog quotas, ResourceUsageDirectory usage) {
    this.subscriptions = subscriptions;
    this.plans = plans;
    this.clock = clock;
    this.quotas = quotas;
    this.usage = usage;
  }

  @Transactional(readOnly = true)
  public SubscriptionView handle(GetMySubscriptionQuery query) {
    var subscription = subscriptions.findByOwnerUserId(query.ownerUserId())
        .orElseThrow(SubscriptionNotFoundException::new);
    var plan = plans.findByCode(subscription.planCode()).orElseThrow(PlanNotFoundException::new);
    var now = clock.instant();
    var status = subscription.status();
    if (status == SubscriptionStatus.ACTIVE && !subscription.isActiveAt(now))
      status = SubscriptionStatus.EXPIRED;
    var quota = quotas.quotaFor(plan.code());
    var consumed = usage.usageOf(query.ownerUserId());
    var usageView = new UsageView(new ResourceUsageView(consumed.sites(), quota.sites()),
        new ResourceUsageView(consumed.floors(), quota.floors()),
        new ResourceUsageView(consumed.beacons(), quota.beacons()));
    return new SubscriptionView(status, plan, subscription.currentPeriodStart(), subscription.currentPeriodEnd(),
        Math.max(0, Duration.between(now, subscription.currentPeriodEnd()).toDays()), quota, usageView);
  }
}
