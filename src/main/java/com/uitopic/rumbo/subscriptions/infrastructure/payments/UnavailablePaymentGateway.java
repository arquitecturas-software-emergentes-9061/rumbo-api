package com.uitopic.rumbo.subscriptions.infrastructure.payments;

import com.uitopic.rumbo.subscriptions.application.ports.PaymentGateway;
import com.uitopic.rumbo.subscriptions.application.exceptions.PaymentsUnavailableException;
public class UnavailablePaymentGateway implements PaymentGateway {
  public CheckoutSession createCheckoutSession(CheckoutRequest request) { throw new PaymentsUnavailableException(); }
}
