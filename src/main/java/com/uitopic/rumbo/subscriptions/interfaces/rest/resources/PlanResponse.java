package com.uitopic.rumbo.subscriptions.interfaces.rest.resources;

import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PlanQuota;

public record PlanResponse(String code, String name, MoneyResponse price,
                           String billingInterval, String purchaseMode, PlanQuota quota) {}
