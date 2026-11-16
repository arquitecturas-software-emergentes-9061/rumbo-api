package com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.mappers;

import com.uitopic.rumbo.subscriptions.domain.model.aggregates.PaymentOrder;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.Money;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.OrganizationRef;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.BillingDetails;
import com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.entities.PaymentOrderJpaEntity;
public final class PaymentOrderPersistenceMapper {
  private PaymentOrderPersistenceMapper() {}
  public static PaymentOrder toDomain(PaymentOrderJpaEntity e) {
    var billing = e.getBillingLegalName() == null && e.getBillingRuc() == null
      && e.getBillingFiscalAddress() == null && e.getBillingEmail() == null ? null
      : new BillingDetails(e.getBillingLegalName(), e.getBillingRuc(), e.getBillingFiscalAddress(), e.getBillingEmail(), e.getBillingPurchaseOrder());
    return new PaymentOrder(e.getId(), e.getPayerUserId(), new OrganizationRef(e.getOrganizationId(), e.getOrganizationName()), billing, e.getPlanCode(), new Money(e.getAmount(), e.getCurrency()),
      e.getStatus(), e.getProviderSessionId(), e.getCreatedAt(), e.getUpdatedAt(), e.getPaidAt());
  }
  public static PaymentOrderJpaEntity toEntity(PaymentOrder o) {
    var e = new PaymentOrderJpaEntity();
    e.setId(o.id()); e.setPayerUserId(o.payerUserId()); e.setPlanCode(o.planCode());
    e.setOrganizationId(o.organization().id()); e.setOrganizationName(o.organization().name());
    if (o.billing() != null) {
      e.setBillingLegalName(o.billing().legalName()); e.setBillingRuc(o.billing().ruc());
      e.setBillingFiscalAddress(o.billing().fiscalAddress()); e.setBillingEmail(o.billing().billingEmail());
      e.setBillingPurchaseOrder(o.billing().purchaseOrder());
    }
    e.setPaidAt(o.paidAt());
    e.setAmount(o.amount().amount()); e.setCurrency(o.amount().currency()); e.setStatus(o.status());
    e.setProviderSessionId(o.providerSessionId()); e.setCreatedAt(o.createdAt()); e.setUpdatedAt(o.updatedAt());
    return e;
  }
}
