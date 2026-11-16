package com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.entities;
import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.*;
@Entity
@Table(name = "subscriptions_subscriptions")
public class SubscriptionJpaEntity {
  @Id
  private UUID id;
  @Column(name = "owner_user_id", nullable = false, unique = true)
  private UUID ownerUserId;
  @Enumerated(EnumType.STRING) @Column(name = "plan_code", nullable = false, length = 30)
  private PlanCode planCode;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
  private SubscriptionStatus status;
  @Column(name = "current_period_start", nullable = false)
  private Instant currentPeriodStart;
  @Column(name = "current_period_end", nullable = false)
  private Instant currentPeriodEnd;
  @Column(name = "provider_subscription_id", length = 255)
  private String providerSubscriptionId;
  @Column(name = "created_at")
  private Instant createdAt;
  @Column(name = "updated_at")
  private Instant updatedAt;
  public UUID getId() { return id; }
  public void setId(UUID value) { this.id = value; }
  public UUID getOwnerUserId() { return ownerUserId; }
  public void setOwnerUserId(UUID value) { this.ownerUserId = value; }
  public PlanCode getPlanCode() { return planCode; }
  public void setPlanCode(PlanCode value) { this.planCode = value; }
  public SubscriptionStatus getStatus() { return status; }
  public void setStatus(SubscriptionStatus value) { this.status = value; }
  public Instant getCurrentPeriodStart() { return currentPeriodStart; }
  public void setCurrentPeriodStart(Instant value) { this.currentPeriodStart = value; }
  public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
  public void setCurrentPeriodEnd(Instant value) { this.currentPeriodEnd = value; }
  public String getProviderSubscriptionId() { return providerSubscriptionId; }
  public void setProviderSubscriptionId(String value) { this.providerSubscriptionId = value; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant value) { this.createdAt = value; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant value) { this.updatedAt = value; }
}
