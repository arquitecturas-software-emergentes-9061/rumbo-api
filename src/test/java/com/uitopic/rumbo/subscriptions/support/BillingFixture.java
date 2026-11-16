package com.uitopic.rumbo.subscriptions.support;

import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.BillingDetails;
import java.util.Map;

public final class BillingFixture {
  private BillingFixture() {}
  public static BillingDetails billing() {
    return new BillingDetails("Clinic Example", "20491823901", "Av. Javier Prado 4200, Lima", "billing@example.test", "PO-2024-089");
  }
  public static Map<String, String> fields() {
    var b = billing();
    return Map.of("legalName", b.legalName(), "ruc", b.ruc(), "fiscalAddress", b.fiscalAddress(),
      "billingEmail", b.billingEmail(), "purchaseOrder", b.purchaseOrder());
  }
}
