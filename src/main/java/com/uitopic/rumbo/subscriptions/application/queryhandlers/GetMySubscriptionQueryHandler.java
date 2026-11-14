package com.uitopic.rumbo.subscriptions.application.queryhandlers;

import com.uitopic.rumbo.subscriptions.domain.exceptions.*;
import com.uitopic.rumbo.subscriptions.domain.model.queries.GetMySubscriptionQuery;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.SubscriptionStatus;
import com.uitopic.rumbo.subscriptions.domain.repositories.*;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetMySubscriptionQueryHandler {
  private final SubscriptionRepository subscriptions;
  private final PlanRepository plans;
  private final Clock clock;

  public GetMySubscriptionQueryHandler(SubscriptionRepository subscriptions, PlanRepository plans, Clock clock) {
    this.subscriptions = subscriptions;
    this.plans = plans;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public SubscriptionView handle(GetMySubscriptionQuery query) {
    var subscription = subscriptions.findByOwnerUserId(query.ownerUserId())
        .orElseThrow(SubscriptionNotFoundException::new);
    var plan = plans.findByCode(subscription.planCode()).orElseThrow(PlanNotFoundException::new);
    var now = clock.instant();
    var status = subscription.status();
    if (status == SubscriptionStatus.ACTIVE && subscription.currentPeriodEnd().isBefore(now))
      status = SubscriptionStatus.EXPIRED;
    return new SubscriptionView(status, plan, subscription.currentPeriodStart(), subscription.currentPeriodEnd(),
        Math.max(0, Duration.between(now, subscription.currentPeriodEnd()).toDays()));
  }
}
