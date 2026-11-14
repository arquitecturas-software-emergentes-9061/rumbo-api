package com.uitopic.rumbo.subscriptions.interfaces;

import com.uitopic.rumbo.subscriptions.application.ports.PaymentGateway;
import com.uitopic.rumbo.subscriptions.SubscriptionTestSeeds;
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

  @Test void basicReturnsExactUsageAndExcludesAnotherOwner() throws Exception {
    String admin = clinicAdmin();
    UUID owner = SubscriptionTestSeeds.ownerOf(admin);
    SubscriptionTestSeeds.subscription(jdbc, owner, "BASIC", false);
    seedResources(owner, 1, 2, 4);
    String other = clinicAdmin();
    UUID otherOwner = SubscriptionTestSeeds.ownerOf(other);
    SubscriptionTestSeeds.activeEnterprise(jdbc, otherOwner);
    seedResources(otherOwner, 2, 3, 5);
    assertUsage(admin, "ACTIVE", 1, 2, 4, 1, 3, 15);
  }

  @Test void enterpriseReturnsNullLimitsAndCountsAcrossSites() throws Exception {
    String admin = clinicAdmin();
    UUID owner = SubscriptionTestSeeds.ownerOf(admin);
    SubscriptionTestSeeds.activeEnterprise(jdbc, owner);
    seedResources(owner, 2, 3, 5);
    assertUsage(admin, "ACTIVE", 2, 6, 10, null, null, null);
  }

  @Test void expiredSubscriptionStillReturnsUsageAndPlanLimits() throws Exception {
    String admin = clinicAdmin();
    UUID owner = SubscriptionTestSeeds.ownerOf(admin);
    SubscriptionTestSeeds.subscription(jdbc, owner, "BASIC", true);
    seedResources(owner, 1, 2, 4);
    assertUsage(admin, "EXPIRED", 1, 2, 4, 1, 3, 15);
  }

  @Test void ownerWithoutClinicHasZeroUsage() throws Exception {
    String admin = token("CLINIC_ADMIN");
    SubscriptionTestSeeds.subscription(jdbc, SubscriptionTestSeeds.ownerOf(admin), "BASIC", false);
    assertUsage(admin, "ACTIVE", 0, 0, 0, 1, 3, 15);
  }

  private void assertUsage(String admin, String expectedStatus, long sites, long floors, long beacons,
      Integer siteLimit, Integer floorLimit, Integer beaconLimit) throws Exception {
    String response = mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(expectedStatus))
        .andReturn().getResponse().getContentAsString();
    var json = mapper.readTree(response);
    String[] kinds = {"sites", "floors", "beacons"};
    long[] used = {sites, floors, beacons};
    Integer[] limits = {siteLimit, floorLimit, beaconLimit};
    assertThat(json.get("usage").size()).isEqualTo(3);
    for (int i = 0; i < kinds.length; i++) {
      var resource = json.at("/usage/" + kinds[i]);
      assertThat(resource.size()).isEqualTo(2);
      assertThat(resource.get("used").asLong()).isEqualTo(used[i]);
      assertThat(resource.get("limit")).isEqualTo(mapper.valueToTree(limits[i]));
      assertThat(json.at("/plan/quota/" + kinds[i])).isEqualTo(mapper.valueToTree(limits[i]));
    }
  }

  private void seedResources(UUID owner, int sites, int floorsPerSite, int beaconsPerSite) {
    UUID clinic = jdbc.queryForObject("SELECT id FROM planning_clinics WHERE owner_user_id = ?", UUID.class, owner);
    for (int s = 0; s < sites; s++) {
      UUID site = UUID.randomUUID();
      jdbc.update("INSERT INTO indoor_sites (id, owner_user_id, clinic_id, name) VALUES (?, ?, ?, 'Usage site')",
          site, owner, clinic);
      UUID firstFloor = null;
      for (int f = 0; f < floorsPerSite; f++) {
        UUID floor = UUID.randomUUID();
        if (firstFloor == null) firstFloor = floor;
        jdbc.update("INSERT INTO planning_floor_sites (floor_id, site_id) VALUES (?, ?)", floor, site);
        jdbc.update("INSERT INTO planning_floors (id, site_id, floor_number, name) VALUES (?, ?, ?, 'Usage floor')",
            floor, site, f);
      }
      for (int b = 0; b < beaconsPerSite; b++) {
        jdbc.update("""
            INSERT INTO positioning_beacons (id, site_id, floor_id, identifier, x, y)
            VALUES (?, ?, ?, ?, 0, 0)
            """, UUID.randomUUID(), site, firstFloor, "usage-" + UUID.randomUUID()); // BLE identifiers are globally unique
      }
    }
  }

  @Test void adminSeesOwnPlanAndPeriodAfterSignedWebhookAndOtherAdminCannotSeeIt() throws Exception {
    when(gateway.createCheckoutSession(any())).thenReturn(
        new PaymentGateway.CheckoutSession("cs_test", "https://checkout.stripe.com/test"));
    String admin = clinicAdmin();
    String order = mvc.perform(post(CHECKOUT).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
        .contentType(MediaType.APPLICATION_JSON).content(checkoutBody("BASIC")))
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
    assertThat(json.size()).isEqualTo(6);
    assertThat(json.get("plan").size()).isEqualTo(5);
    assertThat(json.at("/plan/quota/sites").asInt()).isEqualTo(1);
    assertThat(json.at("/plan/quota/floors").asInt()).isEqualTo(3);
    assertThat(json.at("/plan/quota/beacons").asInt()).isEqualTo(15);
    var start = ((java.sql.Timestamp) persisted.get("current_period_start")).toInstant();
    var end = ((java.sql.Timestamp) persisted.get("current_period_end")).toInstant();
    assertThat(Instant.parse(json.get("currentPeriodStart").asText())).isEqualTo(start);
    assertThat(Instant.parse(json.get("currentPeriodEnd").asText())).isEqualTo(end);
    assertThat(json.get("daysRemaining").asLong()).isBetween(
        Math.max(0, Duration.between(Instant.now(), end).toDays()),
        Math.max(0, Duration.between(beforeQuery, end).toDays()));
    assertThat(response).doesNotContain("providerSubscriptionId", "sub_test", "cs_test");
    mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdmin()))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_FOUND"));
  }

  @Test void adminWithoutSubscriptionGetsNotFound() throws Exception {
    mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdmin()))
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
