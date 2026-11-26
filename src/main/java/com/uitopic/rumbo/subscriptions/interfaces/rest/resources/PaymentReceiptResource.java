package com.uitopic.rumbo.subscriptions.interfaces.rest.resources;

import java.time.Instant;

import com.uitopic.rumbo.subscriptions.domain.model.aggregates.PaymentOrder;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PaymentOrderStatus;

/** Internal payment record. It is not an electronic tax document. */
public record PaymentReceiptResource(String number, Instant issuedAt) {
  public static PaymentReceiptResource from(PaymentOrder order) {
    if (order.status() != PaymentOrderStatus.PAID || order.paidAt() == null) {
      return null;
    }
    return new PaymentReceiptResource("RA-" + order.id(), order.paidAt());
  }
}
