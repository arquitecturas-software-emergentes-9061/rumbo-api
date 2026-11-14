package com.uitopic.rumbo.subscriptions.infrastructure.payments.stripe;

import com.stripe.net.Webhook;
import com.stripe.model.checkout.Session;
import com.uitopic.rumbo.subscriptions.application.ports.*;
import com.uitopic.rumbo.subscriptions.application.exceptions.InvalidWebhookSignatureException;
import java.util.UUID;

public class StripePaymentEventVerifier implements PaymentEventVerifier {
  private final String secret;
  public StripePaymentEventVerifier(String secret) { this.secret = secret; }
  public PaymentEvent verify(String payload, String signatureHeader) {
    if (signatureHeader == null || signatureHeader.isBlank()) throw new InvalidWebhookSignatureException();
    try {
      var event = Webhook.constructEvent(payload, signatureHeader, secret, 300);
      if (event.getId() == null || event.getId().isBlank() || event.getId().length() > 255
          || event.getType() == null || event.getType().length() > 100) throw new InvalidWebhookSignatureException();
      if (!"checkout.session.completed".equals(event.getType()) && !"checkout.session.expired".equals(event.getType()))
        return new PaymentEvent(event.getId(), event.getType(), null, null, false);
      var deserializer = event.getDataObjectDeserializer();
      // Stripe can deliver an older API version than the SDK. Signature is already verified;
      // use the SDK's unsafe deserialization only to read stable checkout Session fields.
      var object = deserializer.getObject().orElse(null);
      if (object == null) object = deserializer.deserializeUnsafe();
      if (!(object instanceof Session session)) throw new InvalidWebhookSignatureException();
      String reference = session.getClientReferenceId();
      if ((reference == null || reference.isBlank()) && session.getMetadata() != null)
        reference = session.getMetadata().get("orderId");
      UUID orderId = null;
      try { if (reference != null) orderId = UUID.fromString(reference); }
      catch (IllegalArgumentException ignored) { /* Unknown order is acknowledged by the handler. */ }
      return new PaymentEvent(event.getId(), event.getType(), orderId, session.getSubscription(), "paid".equals(session.getPaymentStatus()));
    } catch (InvalidWebhookSignatureException e) { throw e; }
    catch (Exception e) { throw new InvalidWebhookSignatureException(); }
  }
}
