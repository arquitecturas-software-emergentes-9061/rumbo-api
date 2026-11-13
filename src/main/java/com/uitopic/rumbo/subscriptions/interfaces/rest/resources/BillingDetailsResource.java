package com.uitopic.rumbo.subscriptions.interfaces.rest.resources;

import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.BillingDetails;

public record BillingDetailsResource(String legalName, String ruc, String fiscalAddress,
    String billingEmail, String purchaseOrder) {
  public BillingDetails toDomain() {
    return new BillingDetails(legalName, ruc, fiscalAddress, billingEmail, purchaseOrder);
  }
  public static BillingDetailsResource from(BillingDetails billing) {
    return billing == null ? null : new BillingDetailsResource(billing.legalName(), billing.ruc(),
      billing.fiscalAddress(), billing.billingEmail(), billing.purchaseOrder());
  }
}
