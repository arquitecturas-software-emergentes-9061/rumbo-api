package com.uitopic.rumbo.subscriptions.domain.model.commands;

import java.util.UUID;
import java.util.Objects;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PlanCode;
public record CreateCheckoutSessionCommand(UUID payerUserId, PlanCode planCode) {
  public CreateCheckoutSessionCommand { Objects.requireNonNull(payerUserId); Objects.requireNonNull(planCode); }
}
