package com.uitopic.rumbo.subscriptions.domain.model.aggregates;

import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.BillingInterval;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.Money;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PlanCode;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PurchaseMode;
import java.util.UUID;

public record Plan(UUID id, PlanCode code, String name, Money price, BillingInterval billingInterval,
                   PurchaseMode purchaseMode, int displayOrder, boolean active) {
  public Plan {
    if (id == null || code == null || price == null || billingInterval == null || purchaseMode == null)
      throw new IllegalArgumentException("Plan identity, code, price, interval and purchase mode are required");
    if (name == null || name.isBlank() || name.length() > 60)
      throw new IllegalArgumentException("Plan name must contain between 1 and 60 characters");
    if (displayOrder < 0) throw new IllegalArgumentException("Display order must not be negative");
  }
}
