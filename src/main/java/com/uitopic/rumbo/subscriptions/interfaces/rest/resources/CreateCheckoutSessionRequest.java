package com.uitopic.rumbo.subscriptions.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PlanCode;
public record CreateCheckoutSessionRequest(@NotNull PlanCode planCode) {}
