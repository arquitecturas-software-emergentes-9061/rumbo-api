package com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.entities;

import jakarta.persistence.*;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.*;
@Entity
@Table(name = "subscriptions_payment_orders")
public class PaymentOrderJpaEntity {
  @Id
  private UUID id;
  @Column(name = "payer_user_id", nullable = false)
  private UUID payerUserId;
  @Enumerated(EnumType.STRING) @Column(name = "plan_code", nullable = false, length = 30)
  private PlanCode planCode;
  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal amount;
  @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 3, columnDefinition = "char(3)")
  private String currency;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
  private PaymentOrderStatus status;
  @Column(name = "provider_session_id", unique = true, length = 255)
  private String providerSessionId;
  @Column(name = "created_at")
  private Instant createdAt;
  @Column(name = "updated_at")
  private Instant updatedAt;
  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getPayerUserId() { return payerUserId; }
  public void setPayerUserId(UUID payerUserId) { this.payerUserId = payerUserId; }
  public PlanCode getPlanCode() { return planCode; }
  public void setPlanCode(PlanCode planCode) { this.planCode = planCode; }
  public BigDecimal getAmount() { return amount; }
  public void setAmount(BigDecimal amount) { this.amount = amount; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public PaymentOrderStatus getStatus() { return status; }
  public void setStatus(PaymentOrderStatus status) { this.status = status; }
  public String getProviderSessionId() { return providerSessionId; }
  public void setProviderSessionId(String providerSessionId) { this.providerSessionId = providerSessionId; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
