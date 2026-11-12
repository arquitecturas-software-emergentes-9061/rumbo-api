package com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.entities;

import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.BillingInterval;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PlanCode;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PurchaseMode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions_plans")
public class PlanJpaEntity {
  @Id
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, unique = true, length = 30)
  private PlanCode code;

  @Column(nullable = false, length = 60)
  private String name;

  @Column(name = "price_amount", nullable = false, precision = 10, scale = 2)
  private BigDecimal priceAmount;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, length = 3, columnDefinition = "char(3)")
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(name = "billing_interval", nullable = false, length = 10)
  private BillingInterval billingInterval;

  @Enumerated(EnumType.STRING)
  @Column(name = "purchase_mode", nullable = false, length = 20)
  private PurchaseMode purchaseMode;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public PlanCode getCode() { return code; }
  public void setCode(PlanCode code) { this.code = code; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public BigDecimal getPriceAmount() { return priceAmount; }
  public void setPriceAmount(BigDecimal priceAmount) { this.priceAmount = priceAmount; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public BillingInterval getBillingInterval() { return billingInterval; }
  public void setBillingInterval(BillingInterval billingInterval) { this.billingInterval = billingInterval; }
  public PurchaseMode getPurchaseMode() { return purchaseMode; }
  public void setPurchaseMode(PurchaseMode purchaseMode) { this.purchaseMode = purchaseMode; }
  public int getDisplayOrder() { return displayOrder; }
  public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
  public boolean getActive() { return active; }
  public void setActive(boolean active) { this.active = active; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
