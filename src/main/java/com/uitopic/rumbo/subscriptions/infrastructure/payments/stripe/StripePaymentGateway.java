package com.uitopic.rumbo.subscriptions.infrastructure.payments.stripe;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.param.checkout.SessionCreateParams;
import com.uitopic.rumbo.subscriptions.application.ports.PaymentGateway;
import com.uitopic.rumbo.subscriptions.application.exceptions.PaymentProviderException;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PlanCode;
import java.util.Map;

public class StripePaymentGateway implements PaymentGateway {
  private final StripeClient client;
  private final Map<PlanCode, String> prices;
  private final String successUrl;
  private final String cancelUrl;
  public StripePaymentGateway(String secretKey, Map<PlanCode, String> prices, String successUrl, String cancelUrl) {
    if (secretKey == null || !(secretKey.startsWith("sk_test_") || secretKey.startsWith("rk_test_")))
      throw new IllegalStateException("Stripe requires a test mode key");
    client = new StripeClient(secretKey);
    this.prices = Map.copyOf(prices); this.successUrl = successUrl; this.cancelUrl = cancelUrl;
  }
  public SessionCreateParams buildParams(CheckoutRequest request) {
    var metadata = Map.of("orderId", request.orderId().toString(), "planCode", request.planCode().name(),
      "payerUserId", request.payerUserId().toString());
    return SessionCreateParams.builder().setMode(SessionCreateParams.Mode.SUBSCRIPTION)
      .addLineItem(SessionCreateParams.LineItem.builder().setPrice(prices.get(request.planCode())).setQuantity(1L).build())
      .setClientReferenceId(request.orderId().toString()).putAllMetadata(metadata)
      .setSubscriptionData(SessionCreateParams.SubscriptionData.builder().putAllMetadata(metadata).build())
      .setSuccessUrl(successUrl.replace("{ORDER_ID}", request.orderId().toString()))
      .setCancelUrl(cancelUrl.replace("{ORDER_ID}", request.orderId().toString())).build();
  }
  @Override
  public CheckoutSession createCheckoutSession(CheckoutRequest request) {
    try {
      var session = client.v1().checkout().sessions().create(buildParams(request));
      return new CheckoutSession(session.getId(), session.getUrl());
    } catch (StripeException ignored) {
      // Never propagate provider messages, request data, or credentials.
      throw new PaymentProviderException();
    }
  }
}
