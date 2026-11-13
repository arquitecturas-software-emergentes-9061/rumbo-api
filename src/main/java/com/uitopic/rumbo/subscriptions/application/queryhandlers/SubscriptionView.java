package com.uitopic.rumbo.subscriptions.application.queryhandlers;

import com.uitopic.rumbo.subscriptions.domain.model.aggregates.Plan;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.SubscriptionStatus;
import java.time.Instant;

public record SubscriptionView(SubscriptionStatus status, Plan plan, Instant currentPeriodStart,
                               Instant currentPeriodEnd, long daysRemaining) {}
