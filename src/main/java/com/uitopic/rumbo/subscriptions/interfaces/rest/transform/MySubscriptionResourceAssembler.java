package com.uitopic.rumbo.subscriptions.interfaces.rest.transform;

import com.uitopic.rumbo.subscriptions.application.queryhandlers.SubscriptionView;
import com.uitopic.rumbo.subscriptions.interfaces.rest.resources.*;

import com.uitopic.rumbo.subscriptions.application.queryhandlers.UsageView.ResourceUsageView;
import com.uitopic.rumbo.subscriptions.interfaces.rest.resources.UsageResource.ResourceUsageResource;

public final class MySubscriptionResourceAssembler {
  private MySubscriptionResourceAssembler() {}

  public static MySubscriptionResponse toResource(SubscriptionView view) {
    var plan = view.plan();
    return new MySubscriptionResponse(view.status().name(),
        new MySubscriptionResponse.SubscriptionPlanResponse(plan.code().name(), plan.name(),
            new MoneyResponse(plan.price().amount(), plan.price().currency()), plan.billingInterval().name(),
            PlanResourceAssembler.toResource(view.quota())),
        view.currentPeriodStart(), view.currentPeriodEnd(), view.daysRemaining(),
        new UsageResource(toResource(view.usage().sites()), toResource(view.usage().floors()),
            toResource(view.usage().beacons())));
  }

  private static ResourceUsageResource toResource(ResourceUsageView usage) {
    return new ResourceUsageResource(usage.used(), usage.limit());
  }
}
