package com.uitopic.rumbo.subscriptions.domain.model.aggregates;

import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.*;
import java.time.*;
import java.util.*;

public class Subscription {
  private final UUID id;
  private final UUID ownerUserId;
  private PlanCode planCode;
  private SubscriptionStatus status;
  private Instant currentPeriodStart;
  private Instant currentPeriodEnd;
  private String providerSubscriptionId;
  private final Instant createdAt;
  private Instant updatedAt;

  public Subscription(UUID id, UUID ownerUserId, PlanCode planCode, SubscriptionStatus status,
      Instant currentPeriodStart, Instant currentPeriodEnd, String providerSubscriptionId,
      Instant createdAt, Instant updatedAt) {
    this.id = Objects.requireNonNull(id);
    this.ownerUserId = Objects.requireNonNull(ownerUserId);
    this.planCode = Objects.requireNonNull(planCode);
    this.status = Objects.requireNonNull(status);
    this.currentPeriodStart = Objects.requireNonNull(currentPeriodStart);
    this.currentPeriodEnd = Objects.requireNonNull(currentPeriodEnd);
    if (!currentPeriodEnd.isAfter(currentPeriodStart)) throw new IllegalArgumentException("Invalid period");
    this.providerSubscriptionId = providerSubscriptionId;
    this.createdAt = Objects.requireNonNull(createdAt);
    this.updatedAt = Objects.requireNonNull(updatedAt);
  }
  public static Subscription activate(UUID ownerUserId, PlanCode planCode, String providerId, Instant now) {
    return new Subscription(UUID.randomUUID(), ownerUserId, planCode, SubscriptionStatus.ACTIVE,
        now, periodEnd(now), providerId, now, now);
  }
  public void changePlan(PlanCode planCode, String providerId, Instant now) {
    this.planCode = Objects.requireNonNull(planCode);
    this.status = SubscriptionStatus.ACTIVE;
    this.currentPeriodStart = Objects.requireNonNull(now);
    this.currentPeriodEnd = periodEnd(now);
    this.providerSubscriptionId = providerId;
    this.updatedAt = now;
  }
  private static Instant periodEnd(Instant now) { return now.atZone(ZoneOffset.UTC).plusMonths(1).toInstant(); }
  public boolean isActiveAt(Instant now) {
    return status == SubscriptionStatus.ACTIVE && !currentPeriodEnd.isBefore(now);
  }
  public UUID id() { return id; }
  public UUID ownerUserId() { return ownerUserId; }
  public PlanCode planCode() { return planCode; }
  public SubscriptionStatus status() { return status; }
  public Instant currentPeriodStart() { return currentPeriodStart; }
  public Instant currentPeriodEnd() { return currentPeriodEnd; }
  public String providerSubscriptionId() { return providerSubscriptionId; }
  public Instant createdAt() { return createdAt; }
  public Instant updatedAt() { return updatedAt; }
}
