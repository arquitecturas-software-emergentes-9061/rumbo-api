package com.uitopic.rumbo.subscriptions.infrastructure.payments;
import com.uitopic.rumbo.subscriptions.application.ports.*;
import com.uitopic.rumbo.subscriptions.application.exceptions.PaymentsUnavailableException;
public class UnavailablePaymentEventVerifier implements PaymentEventVerifier {
  public PaymentEvent verify(String payload, String signatureHeader) { throw new PaymentsUnavailableException(); }
}
