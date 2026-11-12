package com.uitopic.rumbo.subscriptions.infrastructure;

import com.uitopic.rumbo.subscriptions.infrastructure.payments.stripe.StripePaymentGateway;
import com.uitopic.rumbo.subscriptions.infrastructure.payments.PaymentGatewayConfiguration;
import com.uitopic.rumbo.subscriptions.infrastructure.payments.UnavailablePaymentGateway;
import com.uitopic.rumbo.subscriptions.application.ports.PaymentGateway.CheckoutRequest;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.*;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
class StripePaymentGatewayTest {
  @Test void buildsSubscriptionParametersWithoutNetwork() {
    // Synthetic non-credential input, never read the environment in unit tests.
    var gateway = new StripePaymentGateway("sk_test_" + "unit-placeholder", Map.of(PlanCode.BASIC, "price_basic"),
      "https://example.test/success?orderId={ORDER_ID}", "https://example.test/cancel?orderId={ORDER_ID}");
    var request = new CheckoutRequest(UUID.randomUUID(), UUID.randomUUID(), PlanCode.BASIC, new Money(new BigDecimal("39.90"), "PEN"));
    var params = gateway.buildParams(request);
    assertThat(params.getMode()).isEqualTo(SessionCreateParams.Mode.SUBSCRIPTION);
    assertThat(params.getLineItems()).hasSize(1);
    assertThat(params.getLineItems().getFirst().getPrice()).isEqualTo("price_basic");
    assertThat(params.getLineItems().getFirst().getQuantity()).isEqualTo(1L);
    assertThat(params.getClientReferenceId()).isEqualTo(request.orderId().toString());
    assertThat(params.getMetadata()).containsExactlyInAnyOrderEntriesOf(Map.of("orderId", request.orderId().toString(),
      "planCode", "BASIC", "payerUserId", request.payerUserId().toString()));
    assertThat(params.getSubscriptionData().getMetadata()).isEqualTo(params.getMetadata());
    assertThat(params.getSuccessUrl()).isEqualTo("https://example.test/success?orderId=" + request.orderId());
    assertThat(params.getCancelUrl()).isEqualTo("https://example.test/cancel?orderId=" + request.orderId());
  }
  @Test void rejectsNonTestKeysWithoutEchoingInput() {
    for (String input : List.of("", "invalid", "sk_live_" + "placeholder", "rk_live_" + "placeholder")) {
      assertThatThrownBy(() -> new StripePaymentGateway(input, Map.of(), "success", "cancel"))
        .isInstanceOf(IllegalStateException.class).hasMessage("Stripe requires a test mode key").hasNoCause();
    }
  }
  @Test void absentConfigurationSelectsUnavailableGateway() {
    assertThat(new PaymentGatewayConfiguration().paymentGateway(new MockEnvironment())).isInstanceOf(UnavailablePaymentGateway.class);
  }
}
