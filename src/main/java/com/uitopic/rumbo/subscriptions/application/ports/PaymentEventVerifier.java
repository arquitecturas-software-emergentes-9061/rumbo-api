package com.uitopic.rumbo.subscriptions.application.ports;
public interface PaymentEventVerifier {
  PaymentEvent verify(String payload, String signatureHeader);
}
