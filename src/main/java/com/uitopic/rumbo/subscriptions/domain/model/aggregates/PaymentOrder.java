package com.uitopic.rumbo.subscriptions.domain.model.aggregates;

import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class PaymentOrder {
  private final UUID id;
  private final UUID payerUserId;
  private final PlanCode planCode;
  private final Money amount;
  private PaymentOrderStatus status;
  private String providerSessionId;
  private final Instant createdAt;
  private Instant updatedAt;

  public PaymentOrder(UUID id, UUID payerUserId, PlanCode planCode, Money amount,
      PaymentOrderStatus status, String providerSessionId, Instant createdAt, Instant updatedAt) {
    this.id = Objects.requireNonNull(id);
    this.payerUserId = Objects.requireNonNull(payerUserId);
    this.planCode = Objects.requireNonNull(planCode);
    this.amount = Objects.requireNonNull(amount);
    this.status = Objects.requireNonNull(status);
    this.providerSessionId = providerSessionId;
    this.createdAt = Objects.requireNonNull(createdAt);
    this.updatedAt = Objects.requireNonNull(updatedAt);
  }
  public static PaymentOrder create(UUID payerUserId, PlanCode planCode, Money amount) {
    Instant now = Instant.now();
    return new PaymentOrder(UUID.randomUUID(), payerUserId, planCode, amount, PaymentOrderStatus.PENDING, null, now, now);
  }
  public void attachCheckoutSession(String sessionId) {
    requirePending();
    if (sessionId == null || sessionId.isBlank() || sessionId.length() > 255)
      throw new IllegalArgumentException("Checkout session is required");
    if (providerSessionId != null) throw new IllegalStateException("Checkout session already attached");
    providerSessionId = sessionId;
    updatedAt = Instant.now();
  }
  private void requirePending() {
    if (status != PaymentOrderStatus.PENDING) throw new IllegalStateException("Payment order is terminal");
  }
  private void transition(PaymentOrderStatus next) { requirePending(); status = next; updatedAt = Instant.now(); }
  public void markFailed() { transition(PaymentOrderStatus.FAILED); }
  public void markPaid() { markPaid(Instant.now()); }
  public void markPaid(Instant now) { if (status == PaymentOrderStatus.PENDING) { status = PaymentOrderStatus.PAID; updatedAt = now; } }
  public void markExpired() { markExpired(Instant.now()); }
  public void markExpired(Instant now) { if (status == PaymentOrderStatus.PENDING) { status = PaymentOrderStatus.EXPIRED; updatedAt = now; } }
  public void markCancelled() { transition(PaymentOrderStatus.CANCELLED); }
  public UUID id() { return id; }
  public UUID payerUserId() { return payerUserId; }
  public PlanCode planCode() { return planCode; }
  public Money amount() { return amount; }
  public PaymentOrderStatus status() { return status; }
  public String providerSessionId() { return providerSessionId; }
  public Instant createdAt() { return createdAt; }
  public Instant updatedAt() { return updatedAt; }
}
