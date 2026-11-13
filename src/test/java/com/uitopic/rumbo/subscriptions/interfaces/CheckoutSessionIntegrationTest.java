package com.uitopic.rumbo.subscriptions.interfaces;

import com.uitopic.rumbo.subscriptions.application.ports.PaymentGateway;
import com.uitopic.rumbo.subscriptions.application.exceptions.PaymentProviderException;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.UUID;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties = "rumbo.subscriptions.stripe.secret-key=")
class CheckoutSessionIntegrationTest extends CheckoutIntegrationSupport {
  @MockitoBean PaymentGateway gateway;
  @BeforeEach void configureGateway() {
    when(gateway.createCheckoutSession(any())).thenAnswer(invocation -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      PaymentGateway.CheckoutRequest request = invocation.getArgument(0);
      assertThat(jdbc.queryForObject("SELECT status FROM subscriptions_payment_orders WHERE id = ?", String.class, request.orderId())).isEqualTo("PENDING");
      return new PaymentGateway.CheckoutSession("session-" + request.orderId(), "https://checkout.stripe.com/test");
    });
  }
  @Test void adminCreatesPendingOrderAndOnlyOwnerCanReadIt() throws Exception {
    String owner = token("CLINIC_ADMIN");
    UUID clinic = registerClinic(owner, "Owner Clinic");
    String body = mvc.perform(post(CHECKOUT).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner)
      .contentType(MediaType.APPLICATION_JSON).content(checkoutBody("BASIC")))
      .andExpect(status().isCreated()).andExpect(jsonPath("$.planCode").value("BASIC"))
      .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.amount").value(39.90))
      .andExpect(jsonPath("$.currency").value("PEN"))
      .andExpect(jsonPath("$.billing.legalName").value("Clinic Example"))
      .andExpect(jsonPath("$.billing.ruc").value("20491823901"))
      .andExpect(jsonPath("$.billing.fiscalAddress").value("Av. Javier Prado 4200, Lima"))
      .andExpect(jsonPath("$.billing.billingEmail").value("billing@example.test"))
      .andExpect(jsonPath("$.billing.purchaseOrder").value("PO-2024-089"))
      .andExpect(jsonPath("$.organization.id").value(clinic.toString()))
      .andExpect(jsonPath("$.organization.name").value("Owner Clinic"))
      .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.stripe.com/test"))
      .andReturn().getResponse().getContentAsString();
    UUID id = UUID.fromString(mapper.readTree(body).get("orderId").asText());
    assertThat(jdbc.queryForMap("SELECT billing_legal_name, billing_ruc, billing_fiscal_address, billing_email, billing_purchase_order FROM subscriptions_payment_orders WHERE id = ?", id))
      .containsEntry("billing_legal_name", "Clinic Example").containsEntry("billing_ruc", "20491823901")
      .containsEntry("billing_fiscal_address", "Av. Javier Prado 4200, Lima")
      .containsEntry("billing_email", "billing@example.test").containsEntry("billing_purchase_order", "PO-2024-089");
    assertThat(jdbc.queryForObject("SELECT organization_id FROM subscriptions_payment_orders WHERE id = ?", UUID.class, id)).isEqualTo(clinic);
    assertThat(jdbc.queryForObject("SELECT organization_name FROM subscriptions_payment_orders WHERE id = ?", String.class, id)).isEqualTo("Owner Clinic");
    assertThat(jdbc.queryForObject("SELECT amount FROM subscriptions_payment_orders WHERE id = ?", BigDecimal.class, id))
      .isEqualByComparingTo("39.90");
    assertThat(jdbc.queryForObject("SELECT provider_session_id FROM subscriptions_payment_orders WHERE id = ?", String.class, id))
      .isEqualTo("session-" + id);
    mvc.perform(get(ORDERS + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
      .andExpect(status().isOk()).andExpect(jsonPath("$.orderId").value(id.toString()))
      .andExpect(jsonPath("$.organization.id").value(clinic.toString()))
      .andExpect(jsonPath("$.organization.name").value("Owner Clinic"))
      .andExpect(jsonPath("$.createdAt").exists()).andExpect(jsonPath("$.checkoutUrl").doesNotExist());
    mvc.perform(get(ORDERS + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdmin()))
      .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PAYMENT_ORDER_NOT_FOUND"));
    mvc.perform(get(ORDERS + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
      .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PAYMENT_ORDER_NOT_FOUND"));
  }
  @Test void adminWithoutClinicCannotCreateOrder() throws Exception {
    String admin = token("CLINIC_ADMIN");
    long before = jdbc.queryForObject("SELECT count(*) FROM subscriptions_payment_orders", Long.class);
    mvc.perform(post(CHECKOUT).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
      .contentType(MediaType.APPLICATION_JSON).content(checkoutBody("BASIC")))
      .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORGANIZATION_REQUIRED"));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM subscriptions_payment_orders", Long.class)).isEqualTo(before);
    verifyNoInteractions(gateway);
  }
  @Test void invalidBillingDoesNotSaveOrCallGateway() throws Exception {
    String admin = clinicAdmin();
    long before = jdbc.queryForObject("SELECT count(*) FROM subscriptions_payment_orders", Long.class);
    var cases = new java.util.LinkedHashMap<String, String>();
    cases.put("legalName", "  ");
    cases.put("ruc", "1234567890");
    cases.put("billingEmail", "invalid-email");
    cases.put("fiscalAddress", "1234");
    cases.put("purchaseOrder", "a".repeat(41));
    for (var invalid : cases.entrySet()) {
      var billing = new java.util.HashMap<>(com.uitopic.rumbo.subscriptions.support.BillingFixture.fields());
      billing.put(invalid.getKey(), invalid.getValue());
      assertInvalidBilling(admin, mapper.writeValueAsString(java.util.Map.of("planCode", "BASIC", "billing", billing)), "billing." + invalid.getKey());
    }
    var letters = new java.util.HashMap<>(com.uitopic.rumbo.subscriptions.support.BillingFixture.fields());
    letters.put("ruc", "2049182390A");
    assertInvalidBilling(admin, mapper.writeValueAsString(java.util.Map.of("planCode", "BASIC", "billing", letters)), "billing.ruc");
    assertInvalidBilling(admin, "{\"planCode\":\"BASIC\"}", "billing");
    assertInvalidBilling(admin, "{\"planCode\":\"BASIC\",\"billing\":null}", "billing");
    assertInvalidBilling(admin, "{\"planCode\":\"BASIC\",\"billing\":{}}", "billing.legalName");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM subscriptions_payment_orders", Long.class)).isEqualTo(before);
    verifyNoInteractions(gateway);
  }
  private void assertInvalidBilling(String admin, String body, String field) throws Exception {
    mvc.perform(post(CHECKOUT).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
      .contentType(MediaType.APPLICATION_JSON).content(body))
      .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
      .andExpect(jsonPath("$.errors['" + field + "']").exists());
  }
  @Test void trimsBillingAndStoresMissingOrBlankPurchaseOrderAsNull() throws Exception {
    String admin = clinicAdmin();
    for (String purchaseOrder : new String[]{null, "  "}) {
      var billing = new java.util.HashMap<>(com.uitopic.rumbo.subscriptions.support.BillingFixture.fields());
      billing.replaceAll((field, value) -> " " + value + " ");
      billing.remove("purchaseOrder");
      if (purchaseOrder != null) billing.put("purchaseOrder", purchaseOrder);
      String response = mvc.perform(post(CHECKOUT).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("planCode", "BASIC", "billing", billing))))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.billing.legalName").value("Clinic Example"))
        .andExpect(jsonPath("$.billing.ruc").value("20491823901"))
        .andExpect(jsonPath("$.billing.fiscalAddress").value("Av. Javier Prado 4200, Lima"))
        .andExpect(jsonPath("$.billing.billingEmail").value("billing@example.test"))
        .andExpect(jsonPath("$.billing.purchaseOrder").value(org.hamcrest.Matchers.nullValue()))
        .andReturn().getResponse().getContentAsString();
      assertThat(mapper.readTree(response).get("billing").has("purchaseOrder")).isTrue();
      UUID id = UUID.fromString(mapper.readTree(response).get("orderId").asText());
      assertThat(jdbc.queryForObject("SELECT billing_purchase_order FROM subscriptions_payment_orders WHERE id = ?", String.class, id)).isNull();
    }
  }
  @Test void billingColumnsAllowHistoricalNullsAndMatchLengths() {
    var columns = jdbc.queryForList("SELECT column_name, data_type, is_nullable, character_maximum_length FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'subscriptions_payment_orders' AND column_name LIKE 'billing_%'");
    assertThat(columns).hasSize(5);
    var lengths = java.util.Map.of("billing_legal_name", 160, "billing_ruc", 11, "billing_fiscal_address", 200, "billing_email", 254, "billing_purchase_order", 40);
    for (var column : columns) {
      assertThat(column).containsEntry("is_nullable", "YES")
        .containsEntry("character_maximum_length", lengths.get(column.get("column_name")))
        .containsEntry("data_type", column.get("column_name").equals("billing_ruc") ? "character" : "character varying");
    }
  }
  @Test void eachAdminContractsForOwnClinic() throws Exception {
    UUID previous = null;
    for (String name : new String[]{"First Clinic", "Second Clinic"}) {
      String admin = token("CLINIC_ADMIN");
      UUID clinic = registerClinic(admin, name);
      assertThat(clinic).isNotEqualTo(previous);
      String response = mvc.perform(post(CHECKOUT).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
        .contentType(MediaType.APPLICATION_JSON).content(checkoutBody("BASIC")))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.organization.id").value(clinic.toString()))
        .andExpect(jsonPath("$.organization.name").value(name)).andReturn().getResponse().getContentAsString();
      UUID order = UUID.fromString(mapper.readTree(response).get("orderId").asText());
      assertThat(jdbc.queryForObject("SELECT o.organization_id FROM subscriptions_payment_orders o JOIN planning_clinics c ON c.owner_user_id = o.payer_user_id WHERE o.id = ? AND o.organization_id = c.id", UUID.class, order)).isEqualTo(clinic);
      previous = clinic;
    }
  }
  @Test void organizationColumnsAreRequired() {
    var columns = jdbc.queryForList("SELECT column_name, data_type, is_nullable, character_maximum_length FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'subscriptions_payment_orders' AND column_name IN ('organization_id', 'organization_name') ORDER BY column_name");
    assertThat(columns).hasSize(2);
    assertThat(columns.get(0)).containsEntry("column_name", "organization_id").containsEntry("data_type", "uuid").containsEntry("is_nullable", "NO");
    assertThat(columns.get(1)).containsEntry("column_name", "organization_name").containsEntry("data_type", "character varying").containsEntry("is_nullable", "NO").containsEntry("character_maximum_length", 160);
  }
  @Test void locationPointsToCreatedOrder() throws Exception {
    var response = mvc.perform(post(CHECKOUT).header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdmin())
      .contentType(MediaType.APPLICATION_JSON).content(checkoutBody("PROFESSIONAL")))
      .andExpect(status().isCreated()).andReturn().getResponse();
    assertThat(response.getHeader("Location")).isEqualTo(ORDERS + mapper.readTree(response.getContentAsString()).get("orderId").asText());
  }
  @Test void enterpriseReturnsConflict() throws Exception {
    mvc.perform(post(CHECKOUT).header(HttpHeaders.AUTHORIZATION, "Bearer " + token("CLINIC_ADMIN"))
      .contentType(MediaType.APPLICATION_JSON).content(checkoutBody("ENTERPRISE")))
      .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_REQUIRES_SALES_CONTACT"));
    verifyNoInteractions(gateway);
  }
  @Test void patientAndAnonymousCannotCreateOrRead() throws Exception {
    String patient = token("PATIENT");
    mvc.perform(post(CHECKOUT).header(HttpHeaders.AUTHORIZATION, "Bearer " + patient)
      .contentType(MediaType.APPLICATION_JSON).content(checkoutBody("BASIC")))
      .andExpect(status().isForbidden());
    mvc.perform(post(CHECKOUT).contentType(MediaType.APPLICATION_JSON).content(checkoutBody("BASIC")))
      .andExpect(status().isUnauthorized());
    mvc.perform(get(ORDERS + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, "Bearer " + patient)).andExpect(status().isForbidden());
    mvc.perform(get(ORDERS + UUID.randomUUID())).andExpect(status().isUnauthorized());
    verifyNoInteractions(gateway);
  }
  @Test void malformedAndEmptyRequestsAreRejected() throws Exception {
    String admin = clinicAdmin();
    for (String body : new String[]{"", "{}", "{\"planCode\":\"UNKNOWN\"}", "{\"planCode\":null}"}) {
      mvc.perform(post(CHECKOUT).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.isIn(new String[]{"VALIDATION_FAILED", "MALFORMED_REQUEST"})));
    }
    verifyNoInteractions(gateway);
  }
  @Test void inactivePlanReturns404WithoutCallingProvider() throws Exception {
    String admin = clinicAdmin();
    jdbc.update("UPDATE subscriptions_plans SET active = false WHERE code = 'BASIC'");
    try {
      mvc.perform(post(CHECKOUT).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
        .contentType(MediaType.APPLICATION_JSON).content(checkoutBody("BASIC")))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));
      verifyNoInteractions(gateway);
    } finally {
      jdbc.update("UPDATE subscriptions_plans SET active = true WHERE code = 'BASIC'");
    }
  }
  @Test void providerFailureIsSanitizedAndCommittedAsFailed() throws Exception {
    doAnswer(invocation -> {
      PaymentGateway.CheckoutRequest request = invocation.getArgument(0);
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      throw new PaymentProviderException();
    }).when(gateway).createCheckoutSession(any());
    long before = jdbc.queryForObject("SELECT count(*) FROM subscriptions_payment_orders WHERE status = 'FAILED'", Long.class);
    mvc.perform(post(CHECKOUT).header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdmin())
      .contentType(MediaType.APPLICATION_JSON).content(checkoutBody("BASIC")))
      .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("PAYMENT_PROVIDER_ERROR"));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM subscriptions_payment_orders WHERE status = 'FAILED'", Long.class)).isEqualTo(before + 1);
  }
}
