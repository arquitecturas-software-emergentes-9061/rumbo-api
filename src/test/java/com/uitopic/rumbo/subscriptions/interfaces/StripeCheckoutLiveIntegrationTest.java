package com.uitopic.rumbo.subscriptions.interfaces;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "STRIPE_SECRET_KEY", matches = "(sk|rk)_test_.+")
class StripeCheckoutLiveIntegrationTest extends CheckoutIntegrationSupport {
  @Test void createsSandboxSessionsThroughApiForBothCheckoutPlans() throws Exception {
    String admin = token("CLINIC_ADMIN");
    for (String code : new String[]{"BASIC", "PROFESSIONAL"}) {
      String body = mvc.perform(post(CHECKOUT).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("planCode", code))))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
      var json = mapper.readTree(body);
      assertThat(json.get("checkoutUrl").asText()).startsWith("https://checkout.stripe.com/");
      assertThat(jdbc.queryForObject("SELECT provider_session_id FROM subscriptions_payment_orders WHERE id = ?",
        String.class, UUID.fromString(json.get("orderId").asText()))).startsWith("cs_test_");
    }
  }
}
