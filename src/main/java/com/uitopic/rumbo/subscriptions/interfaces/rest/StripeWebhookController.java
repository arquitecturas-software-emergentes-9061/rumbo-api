package com.uitopic.rumbo.subscriptions.interfaces.rest;
import com.uitopic.rumbo.subscriptions.application.ports.PaymentEventVerifier;
import com.uitopic.rumbo.subscriptions.application.commandhandlers.ConfirmPaymentCommandHandler;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/subscriptions/stripe/webhook")
public class StripeWebhookController {
  private final PaymentEventVerifier verifier;
  private final ConfirmPaymentCommandHandler handler;
  public StripeWebhookController(PaymentEventVerifier verifier, ConfirmPaymentCommandHandler handler) {
    this.verifier = verifier; this.handler = handler;
  }
  @PostMapping
  public void receive(@RequestBody(required = false) String payload,
      @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
    handler.handle(verifier.verify(payload, signature));
  }
}
