package com.uitopic.rumbo.subscriptions.interfaces.rest.transform;

import com.uitopic.rumbo.subscriptions.application.queryhandlers.PlanView;
import com.uitopic.rumbo.subscriptions.interfaces.rest.resources.PlanQuotaResource;
import com.uitopic.rumbo.subscriptions.interfaces.rest.resources.MoneyResponse;
import com.uitopic.rumbo.subscriptions.interfaces.rest.resources.PlanResponse;

import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PlanQuota;

public final class PlanResourceAssembler {
  private PlanResourceAssembler() {}

  public static PlanResponse toResource(PlanView view) {
    var plan = view.plan();
    return new PlanResponse(plan.code().name(), plan.name(),
      new MoneyResponse(plan.price().amount(), plan.price().currency()),
      plan.billingInterval().name(), plan.purchaseMode().name(), toResource(view.quota()));
  }

  public static PlanQuotaResource toResource(PlanQuota quota) {
    return new PlanQuotaResource(quota.sites(), quota.floors(), quota.beacons());
  }
}
