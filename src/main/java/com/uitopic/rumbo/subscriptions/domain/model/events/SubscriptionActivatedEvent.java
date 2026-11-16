package com.uitopic.rumbo.subscriptions.domain.model.events;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PlanCode;
import java.time.Instant;
import java.util.UUID;
public record SubscriptionActivatedEvent(UUID ownerUserId, PlanCode planCode,
    Instant currentPeriodStart, Instant currentPeriodEnd) { }
