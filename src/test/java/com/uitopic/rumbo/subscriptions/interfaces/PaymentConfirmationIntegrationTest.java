package com.uitopic.rumbo.subscriptions.interfaces;

import com.uitopic.rumbo.subscriptions.application.ports.PaymentGateway;
import com.uitopic.rumbo.subscriptions.application.commandhandlers.ConfirmPaymentCommandHandler;
import com.uitopic.rumbo.subscriptions.application.ports.PaymentEventVerifier;
import com.uitopic.rumbo.subscriptions.domain.model.events.SubscriptionActivatedEvent;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;
import java.util.concurrent.*;
import static com.uitopic.rumbo.subscriptions.support.StripeWebhookFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "rumbo.subscriptions.stripe.secret-key=")
@RecordApplicationEvents
class PaymentConfirmationIntegrationTest extends CheckoutIntegrationSupport {
  static final String WEBHOOK = "/api/v1/subscriptions/stripe/webhook";
  private static final String SECRET = randomSecret();
  @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
    registry.add("rumbo.subscriptions.stripe.webhook-secret", () -> SECRET);
  }
  @MockitoBean PaymentGateway gateway;
  @Autowired ApplicationEvents applicationEvents;
  @Autowired ConfirmPaymentCommandHandler handler;
  @Autowired PaymentEventVerifier verifier;
  @Autowired PlatformTransactionManager transactionManager;
  @BeforeEach void gateway() {
    when(gateway.createCheckoutSession(any())).thenAnswer(invocation -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      PaymentGateway.CheckoutRequest request = invocation.getArgument(0);
      return new PaymentGateway.CheckoutSession("session-" + request.orderId(), "https://checkout.stripe.com/test");
    });
  }
  UUID order(String token, String plan) throws Exception {
    String response = mvc.perform(post(CHECKOUT).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON).content(checkoutBody(plan)))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return UUID.fromString(mapper.readTree(response).get("orderId").asText());
  }
  String eventId() { return "evt_" + UUID.randomUUID(); }
  void deliver(String body) throws Exception {
    mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body)
        .header("Stripe-Signature", signature(body, SECRET))).andExpect(status().isOk());
  }
  String state(UUID order) {
    return jdbc.queryForObject("SELECT status FROM subscriptions_payment_orders WHERE id = ?", String.class, order);
  }
  UUID owner(UUID order) {
    return jdbc.queryForObject("SELECT payer_user_id FROM subscriptions_payment_orders WHERE id = ?", UUID.class, order);
  }
  Map<String, Object> subscription(UUID order) {
    return jdbc.queryForMap("SELECT * FROM subscriptions_subscriptions WHERE owner_user_id = ?", owner(order));
  }
  long subscriptions(UUID order) {
    return jdbc.queryForObject("SELECT count(*) FROM subscriptions_subscriptions WHERE owner_user_id = ?", Long.class, owner(order));
  }
  @Test void signedWebhookActivatesAndDuplicatesDoNotRestartPeriod() throws Exception {
    String admin = clinicAdmin();
    UUID order = order(admin, "BASIC");
    String id = eventId();
    String body = payload(id, "checkout.session.completed", order.toString(), true);
    deliver(body);
    assertThat(state(order)).isEqualTo("PAID");
    String response = mvc.perform(get(ORDERS + order).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
      .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"))
      .andExpect(jsonPath("$.paidAt").exists())
      .andExpect(jsonPath("$.billing.legalName").value("Clinic Example"))
      .andExpect(jsonPath("$.billing.ruc").value("20491823901"))
      .andExpect(jsonPath("$.billing.fiscalAddress").value("Av. Javier Prado 4200, Lima"))
      .andExpect(jsonPath("$.billing.billingEmail").value("billing@example.test"))
      .andExpect(jsonPath("$.billing.purchaseOrder").value("PO-2024-089"))
      .andReturn().getResponse().getContentAsString();
    var paidAt = jdbc.queryForObject("SELECT paid_at FROM subscriptions_payment_orders WHERE id = ?", java.sql.Timestamp.class, order).toInstant();
    assertThat(java.time.Instant.parse(mapper.readTree(response).get("paidAt").asText())).isEqualTo(paidAt);
    var first = subscription(order);
    assertThat(first.get("status")).isEqualTo("ACTIVE");
    assertThat(first.get("plan_code")).isEqualTo("BASIC");
    assertThat(first.get("provider_subscription_id")).isEqualTo("sub_test");
    var start = ((java.sql.Timestamp) first.get("current_period_start")).toInstant();
    var end = ((java.sql.Timestamp) first.get("current_period_end")).toInstant();
    assertThat(end).isEqualTo(start.atZone(java.time.ZoneOffset.UTC).plusMonths(1).toInstant());
    deliver(body);
    deliver(payload(eventId(), "checkout.session.completed", order.toString(), true));
    assertThat(subscription(order)).isEqualTo(first);
    assertThat(jdbc.queryForObject("SELECT paid_at FROM subscriptions_payment_orders WHERE id = ?", java.sql.Timestamp.class, order).toInstant()).isEqualTo(paidAt);
    assertThat(subscriptions(order)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM subscriptions_processed_payment_events WHERE event_id = ?", Long.class, id)).isEqualTo(1);
    assertThat(applicationEvents.stream(SubscriptionActivatedEvent.class).count()).isEqualTo(1);
  }
  @Test void invalidMissingAndExpiredSignaturesDoNotChangeDatabase() throws Exception {
    UUID order = order(clinicAdmin(), "BASIC");
    String id = eventId();
    String body = payload(id, "checkout.session.completed", order.toString(), true);
    for (String header : new String[]{signature(body, randomSecret()), signature(body, SECRET, java.time.Instant.now().minusSeconds(600).getEpochSecond())}) {
      mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body).header("Stripe-Signature", header))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));
    }
    mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));
    assertThat(state(order)).isEqualTo("PENDING");
    assertThat(subscriptions(order)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM subscriptions_processed_payment_events WHERE event_id = ?", Long.class, id)).isZero();
  }
  @Test void expirationDoesNotActivateAndLaterPaymentCannotChangeTerminalOrder() throws Exception {
    UUID order = order(clinicAdmin(), "BASIC");
    deliver(payload(eventId(), "checkout.session.expired", order.toString(), false));
    deliver(payload(eventId(), "checkout.session.completed", order.toString(), true));
    assertThat(state(order)).isEqualTo("EXPIRED");
    assertThat(subscriptions(order)).isZero();
  }
  @Test void unpaidIgnoredAndUnknownOrdersAreAcknowledgedAndRecorded() throws Exception {
    UUID order = order(clinicAdmin(), "BASIC");
    for (String type : new String[]{"checkout.session.completed", "invoice.paid", "customer.subscription.updated"}) {
      String id = eventId();
      deliver(payload(id, type, order.toString(), false));
      assertThat(jdbc.queryForObject("SELECT count(*) FROM subscriptions_processed_payment_events WHERE event_id = ?", Long.class, id)).isEqualTo(1);
    }
    deliver(payload(eventId(), "checkout.session.completed", UUID.randomUUID().toString(), true));
    assertThat(state(order)).isEqualTo("PENDING");
    assertThat(subscriptions(order)).isZero();
  }
  @Test void signedDenialsLeaveNewOrderPendingAndPermitASuccessfulRetry() throws Exception {
    String token = clinicAdmin();
    UUID pending = order(token, "BASIC");
    deliver(declinedIntent(pending));
    deliver(payload(eventId(), "checkout.session.async_payment_failed", pending.toString(), false));

    assertThat(subscriptions(pending)).isZero();
    mvc.perform(get("/api/v1/subscriptions/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
      .andExpect(status().isNotFound());
    assertOrderStatus(token, pending, "PENDING");

    deliver(payload(eventId(), "checkout.session.completed", pending.toString(), true));
    assertOrderStatus(token, pending, "PAID");
    assertThat(subscription(pending).get("plan_code")).isEqualTo("BASIC");
  }

  @Test void signedDenialsAndExpirationPreserveExistingPlanAndPeriod() throws Exception {
    String token = clinicAdmin();
    UUID active = order(token, "BASIC");
    deliver(payload(eventId(), "checkout.session.completed", active.toString(), true));
    var previous = subscription(active);
    UUID pending = order(token, "PROFESSIONAL");

    deliver(declinedIntent(pending));
    deliver(payload(eventId(), "checkout.session.async_payment_failed", pending.toString(), false));
    assertOrderStatus(token, pending, "PENDING");
    assertThat(subscription(active)).isEqualTo(previous);

    deliver(payload(eventId(), "checkout.session.expired", pending.toString(), false));
    assertOrderStatus(token, pending, "EXPIRED");
    assertThat(subscription(active)).isEqualTo(previous);
    mvc.perform(get("/api/v1/subscriptions/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
      .andExpect(status().isOk()).andExpect(jsonPath("$.plan.code").value("BASIC"));
  }

  private String declinedIntent(UUID pending) {
    return """
      {"id":"%s","object":"event","api_version":"2020-08-27","type":"payment_intent.payment_failed",
       "data":{"object":{"id":"pi_test","object":"payment_intent",
       "last_payment_error":{"code":"card_declined"},"metadata":{"orderId":"%s"}}}}
      """.formatted(eventId(), pending);
  }

  private void assertOrderStatus(String token, UUID order, String expected) throws Exception {
    mvc.perform(get(ORDERS + order).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
      .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(expected));
  }

  @Test void secondPaidOrderReplacesPlanOnSameSubscription() throws Exception {
    String token = clinicAdmin();
    UUID first = order(token, "BASIC");
    deliver(payload(eventId(), "checkout.session.completed", first.toString(), true));
    var before = subscription(first);
    UUID second = order(token, "PROFESSIONAL");
    deliver(payload(eventId(), "checkout.session.completed", second.toString(), true));
    var after = subscription(second);
    assertThat(after.get("id")).isEqualTo(before.get("id"));
    assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
    assertThat(after.get("plan_code")).isEqualTo("PROFESSIONAL");
    assertThat(subscriptions(second)).isEqualTo(1);
  }
  @Test void onlyPostWebhookIsPublic() throws Exception {
    mvc.perform(get(WEBHOOK)).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/subscriptions/stripe/other").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }
  @Test void rollbackIncludesEventOrderAndSubscriptionAndAllowsRetry() throws Exception {
    UUID order = order(clinicAdmin(), "BASIC");
    String id = eventId();
    String body = payload(id, "checkout.session.completed", order.toString(), true);
    var event = verifier.verify(body, signature(body, SECRET));
    new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
      handler.handle(event);
      transaction.setRollbackOnly();
    });
    assertThat(state(order)).isEqualTo("PENDING");
    assertThat(subscriptions(order)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM subscriptions_processed_payment_events WHERE event_id = ?", Long.class, id)).isZero();
    deliver(body);
    assertThat(state(order)).isEqualTo("PAID");
    assertThat(subscriptions(order)).isEqualTo(1);
  }
  @Test void concurrentDuplicateDeliveriesActivateOnce() throws Exception {
    UUID order = order(clinicAdmin(), "BASIC");
    String body = payload(eventId(), "checkout.session.completed", order.toString(), true);
    concurrentDeliveries(body, body);
    assertThat(state(order)).isEqualTo("PAID");
    assertThat(subscriptions(order)).isEqualTo(1);
  }
  @Test void concurrentOrdersForSameOwnerCreateOneSubscription() throws Exception {
    String token = clinicAdmin();
    UUID first = order(token, "BASIC");
    UUID second = order(token, "PROFESSIONAL");
    concurrentDeliveries(payload(eventId(), "checkout.session.completed", first.toString(), true),
        payload(eventId(), "checkout.session.completed", second.toString(), true));
    assertThat(state(first)).isEqualTo("PAID");
    assertThat(state(second)).isEqualTo("PAID");
    assertThat(subscriptions(first)).isEqualTo(1);
  }
  void concurrentDeliveries(String first, String second) throws Exception {
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      List<Future<?>> futures = new ArrayList<>();
      for (String body : List.of(first, second)) futures.add(executor.submit(() -> {
        ready.countDown();
        try {
          if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start timed out");
          deliver(body);
        } catch (Exception e) { throw new RuntimeException(e); }
      }));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      for (var future : futures) future.get(20, TimeUnit.SECONDS);
    }
  }
}
