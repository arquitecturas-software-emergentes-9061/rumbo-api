package com.uitopic.rumbo.subscriptions.infrastructure.payments;

import com.uitopic.rumbo.subscriptions.application.ports.PaymentGateway;
import com.uitopic.rumbo.subscriptions.infrastructure.payments.stripe.StripePaymentGateway;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PlanCode;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import java.util.Map;
@Configuration
public class PaymentGatewayConfiguration {
  @Bean
  public PaymentGateway paymentGateway(Environment environment) {
    String key = environment.getProperty("rumbo.subscriptions.stripe.secret-key", "");
    if (key.isEmpty()) return new UnavailablePaymentGateway();
    return new StripePaymentGateway(key, Map.of(
      PlanCode.BASIC, environment.getRequiredProperty("rumbo.subscriptions.stripe.prices.BASIC"),
      PlanCode.PROFESSIONAL, environment.getRequiredProperty("rumbo.subscriptions.stripe.prices.PROFESSIONAL"),
      PlanCode.ENTERPRISE, environment.getRequiredProperty("rumbo.subscriptions.stripe.prices.ENTERPRISE")),
      environment.getRequiredProperty("rumbo.subscriptions.checkout.success-url"),
      environment.getRequiredProperty("rumbo.subscriptions.checkout.cancel-url"));
  }
}
