package com.uitopic.rumbo.subscriptions.infrastructure.payments;
import com.uitopic.rumbo.subscriptions.application.ports.PaymentEventVerifier;
import com.uitopic.rumbo.subscriptions.infrastructure.payments.stripe.StripePaymentEventVerifier;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
@Configuration
public class PaymentEventVerifierConfiguration {
  @Bean
  public PaymentEventVerifier paymentEventVerifier(@Value("${rumbo.subscriptions.stripe.webhook-secret:}") String secret) {
    return secret.isBlank() ? new UnavailablePaymentEventVerifier() : new StripePaymentEventVerifier(secret);
  }
}
