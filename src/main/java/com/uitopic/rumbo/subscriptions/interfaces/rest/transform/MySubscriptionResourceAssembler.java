package com.uitopic.rumbo.subscriptions.interfaces.rest.transform;

import com.uitopic.rumbo.subscriptions.application.queryhandlers.SubscriptionView;
import com.uitopic.rumbo.subscriptions.interfaces.rest.resources.*;

import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PlanQuota;

public final class MySubscriptionResourceAssembler {
  private MySubscriptionResourceAssembler() {}

  public static MySubscriptionResponse toResource(SubscriptionView view, PlanQuota quota) {
    var plan = view.plan();
    return new MySubscriptionResponse(view.status().name(),
        new MySubscriptionResponse.SubscriptionPlanResponse(plan.code().name(), plan.name(),
            new MoneyResponse(plan.price().amount(), plan.price().currency()), plan.billingInterval().name(), quota),
        view.currentPeriodStart(), view.currentPeriodEnd(), view.daysRemaining());
  }
}
