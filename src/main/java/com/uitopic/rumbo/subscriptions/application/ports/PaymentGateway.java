package com.uitopic.rumbo.subscriptions.application.ports;

import java.util.UUID;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.*;
public interface PaymentGateway {
  CheckoutSession createCheckoutSession(CheckoutRequest request);
  record CheckoutRequest(UUID orderId, UUID payerUserId, PlanCode planCode, Money amount) {}
  record CheckoutSession(String sessionId, String url) {}
}
