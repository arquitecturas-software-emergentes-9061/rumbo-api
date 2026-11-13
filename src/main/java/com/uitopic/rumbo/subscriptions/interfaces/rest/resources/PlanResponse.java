package com.uitopic.rumbo.subscriptions.interfaces.rest.resources;

public record PlanResponse(String code, String name, MoneyResponse price,
                           String billingInterval, String purchaseMode) {}
