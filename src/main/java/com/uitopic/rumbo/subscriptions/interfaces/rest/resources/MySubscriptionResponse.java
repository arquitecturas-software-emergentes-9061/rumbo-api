package com.uitopic.rumbo.subscriptions.interfaces.rest.resources;

import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PlanQuota;

import java.time.Instant;

public record MySubscriptionResponse(String status, SubscriptionPlanResponse plan, Instant currentPeriodStart,
                                     Instant currentPeriodEnd, long daysRemaining) {
  public record SubscriptionPlanResponse(String code, String name, MoneyResponse price, String billingInterval, PlanQuota quota) {}
}
