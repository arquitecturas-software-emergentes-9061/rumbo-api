package com.uitopic.rumbo.subscriptions.interfaces;

import com.uitopic.rumbo.subscriptions.application.ports.PaymentGateway;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static com.uitopic.rumbo.subscriptions.support.StripeWebhookFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "rumbo.subscriptions.stripe.secret-key=")
class MySubscriptionIntegrationTest extends CheckoutIntegrationSupport {
  private static final String PATH = "/api/v1/subscriptions/me";
  private static final String SECRET = randomSecret();
  @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
    registry.add("rumbo.subscriptions.stripe.webhook-secret", () -> SECRET);
  }
  @MockitoBean PaymentGateway gateway;

  @Test void adminSeesOwnPlanAndPeriodAfterSignedWebhookAndOtherAdminCannotSeeIt() throws Exception {
    when(gateway.createCheckoutSession(any())).thenReturn(
        new PaymentGateway.CheckoutSession("cs_test", "https://checkout.stripe.com/test"));
    String admin = token("CLINIC_ADMIN");
    String order = mvc.perform(post(CHECKOUT).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
        .contentType(MediaType.APPLICATION_JSON).content("{\"planCode\":\"BASIC\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String orderId = mapper.readTree(order).get("orderId").asText();
    String body = payload("evt_" + UUID.randomUUID(), "checkout.session.completed", orderId, true);
    mvc.perform(post("/api/v1/subscriptions/stripe/webhook").contentType(MediaType.APPLICATION_JSON)
        .content(body).header("Stripe-Signature", signature(body, SECRET))).andExpect(status().isOk());
    var persisted = jdbc.queryForMap("""
        SELECT s.current_period_start, s.current_period_end FROM subscriptions_subscriptions s
        JOIN subscriptions_payment_orders o ON s.owner_user_id = o.payer_user_id WHERE o.id = ?
        """, UUID.fromString(orderId));
    Instant beforeQuery = Instant.now();
    String response = mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
        .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.plan.code").value("BASIC"))
        .andExpect(jsonPath("$.plan.name").value("Básico"))
        .andExpect(jsonPath("$.plan.price.amount").value(39.90))
        .andExpect(jsonPath("$.plan.price.currency").value("PEN"))
        .andExpect(jsonPath("$.plan.billingInterval").value("MONTH"))
        .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    var json = mapper.readTree(response);
    assertThat(json.size()).isEqualTo(5);
    assertThat(json.get("plan").size()).isEqualTo(4);
    var start = ((java.sql.Timestamp) persisted.get("current_period_start")).toInstant();
    var end = ((java.sql.Timestamp) persisted.get("current_period_end")).toInstant();
    assertThat(Instant.parse(json.get("currentPeriodStart").asText())).isEqualTo(start);
    assertThat(Instant.parse(json.get("currentPeriodEnd").asText())).isEqualTo(end);
    assertThat(json.get("daysRemaining").asLong()).isBetween(
        Math.max(0, Duration.between(Instant.now(), end).toDays()),
        Math.max(0, Duration.between(beforeQuery, end).toDays()));
    assertThat(response).doesNotContain("providerSubscriptionId", "sub_test", "cs_test");
    mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token("CLINIC_ADMIN")))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_FOUND"));
  }

  @Test void adminWithoutSubscriptionGetsNotFound() throws Exception {
    mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token("CLINIC_ADMIN")))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_FOUND"));
  }

  @Test void patientIsForbidden() throws Exception {
    mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token("PATIENT")))
        .andExpect(status().isForbidden());
  }

  @Test void anonymousIsUnauthorized() throws Exception {
    mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
  }
}
