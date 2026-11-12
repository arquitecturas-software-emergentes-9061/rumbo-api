package com.uitopic.rumbo.subscriptions.interfaces.rest.transform;

import com.uitopic.rumbo.subscriptions.application.queryhandlers.SubscriptionView;
import com.uitopic.rumbo.subscriptions.interfaces.rest.resources.*;

public final class MySubscriptionResourceAssembler {
  private MySubscriptionResourceAssembler() {}

  public static MySubscriptionResponse toResource(SubscriptionView view) {
    var plan = view.plan();
    return new MySubscriptionResponse(view.status().name(),
        new MySubscriptionResponse.SubscriptionPlanResponse(plan.code().name(), plan.name(),
            new MoneyResponse(plan.price().amount(), plan.price().currency()), plan.billingInterval().name()),
        view.currentPeriodStart(), view.currentPeriodEnd(), view.daysRemaining());
  }
}
