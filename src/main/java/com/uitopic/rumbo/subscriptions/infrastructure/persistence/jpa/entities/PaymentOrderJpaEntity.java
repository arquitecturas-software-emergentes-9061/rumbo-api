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
  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;
  @Column(name = "organization_name", nullable = false, length = 160)
  private String organizationName;
  @Column(name = "billing_legal_name", length = 160)
  private String billingLegalName;
  @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "billing_ruc", length = 11, columnDefinition = "char(11)")
  private String billingRuc;
  @Column(name = "billing_fiscal_address", length = 200)
  private String billingFiscalAddress;
  @Column(name = "billing_email", length = 254)
  private String billingEmail;
  @Column(name = "billing_purchase_order", length = 40)
  private String billingPurchaseOrder;
  @Column(name = "paid_at")
  private Instant paidAt;
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
  public String getBillingLegalName() { return billingLegalName; }
  public void setBillingLegalName(String value) { billingLegalName = value; }
  public String getBillingRuc() { return billingRuc; }
  public void setBillingRuc(String value) { billingRuc = value; }
  public String getBillingFiscalAddress() { return billingFiscalAddress; }
  public void setBillingFiscalAddress(String value) { billingFiscalAddress = value; }
  public String getBillingEmail() { return billingEmail; }
  public void setBillingEmail(String value) { billingEmail = value; }
  public String getBillingPurchaseOrder() { return billingPurchaseOrder; }
  public void setBillingPurchaseOrder(String value) { billingPurchaseOrder = value; }
  public Instant getPaidAt() { return paidAt; }
  public void setPaidAt(Instant value) { paidAt = value; }
  public void setId(UUID id) { this.id = id; }
  public UUID getPayerUserId() { return payerUserId; }
  public void setPayerUserId(UUID payerUserId) { this.payerUserId = payerUserId; }
  public UUID getOrganizationId() { return organizationId; }
  public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
  public String getOrganizationName() { return organizationName; }
  public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }
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
