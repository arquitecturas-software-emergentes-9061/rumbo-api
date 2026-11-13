package com.uitopic.rumbo.subscriptions.interfaces.rest.transform;

import com.uitopic.rumbo.subscriptions.interfaces.rest.resources.*;
import com.uitopic.rumbo.subscriptions.application.commandhandlers.CreateCheckoutSessionCommandHandler.Result;
import com.uitopic.rumbo.subscriptions.domain.model.aggregates.PaymentOrder;
public final class PaymentOrderResourceAssembler {
  private PaymentOrderResourceAssembler() {}
  public static CheckoutSessionResponse toResource(Result result) {
    var o = result.order();
    return new CheckoutSessionResponse(o.id(), o.planCode(), o.status(), o.amount().amount(), o.amount().currency(), result.checkoutUrl());
  }
  public static PaymentOrderResponse toResource(PaymentOrder o) {
    return new PaymentOrderResponse(o.id(), o.planCode(), o.status(), o.amount().amount(), o.amount().currency(), o.createdAt());
  }
}
