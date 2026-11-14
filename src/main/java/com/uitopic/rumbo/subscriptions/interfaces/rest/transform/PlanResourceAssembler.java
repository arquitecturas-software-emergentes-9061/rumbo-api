package com.uitopic.rumbo.subscriptions.interfaces.rest.transform;

import com.uitopic.rumbo.subscriptions.domain.model.aggregates.Plan;
import com.uitopic.rumbo.subscriptions.interfaces.rest.resources.MoneyResponse;
import com.uitopic.rumbo.subscriptions.interfaces.rest.resources.PlanResponse;

import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PlanQuota;

public final class PlanResourceAssembler {
  private PlanResourceAssembler() {}

  public static PlanResponse toResource(Plan plan, PlanQuota quota) {
    return new PlanResponse(plan.code().name(), plan.name(),
      new MoneyResponse(plan.price().amount(), plan.price().currency()),
      plan.billingInterval().name(), plan.purchaseMode().name(), quota);
  }
}
