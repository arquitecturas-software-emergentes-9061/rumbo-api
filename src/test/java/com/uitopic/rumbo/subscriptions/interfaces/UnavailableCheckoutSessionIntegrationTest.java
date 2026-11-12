package com.uitopic.rumbo.subscriptions.interfaces;

import com.uitopic.rumbo.subscriptions.application.ports.PaymentGateway;
import com.uitopic.rumbo.subscriptions.infrastructure.payments.UnavailablePaymentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties = "rumbo.subscriptions.stripe.secret-key=")
class UnavailableCheckoutSessionIntegrationTest extends CheckoutIntegrationSupport {
  @Autowired PaymentGateway gateway;
  @Test void missingKeyUsesUnavailableBeanAndReturns503() throws Exception {
    assertThat(gateway).isInstanceOf(UnavailablePaymentGateway.class);
    mvc.perform(post(CHECKOUT).header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdmin())
      .contentType(MediaType.APPLICATION_JSON).content("{\"planCode\":\"BASIC\"}"))
      .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PAYMENTS_UNAVAILABLE"));
  }
}
