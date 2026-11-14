package com.uitopic.rumbo.subscriptions.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PlanCode;
import com.uitopic.rumbo.subscriptions.domain.repositories.PlanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class PlanCatalogIntegrationTest {
  private static final String PATH = "/api/v1/subscriptions/plans";
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlanRepository plans;

  @Test
  void adminReceivesTheExactOrderedMonthlyCatalog() throws Exception {
    String body = mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token("CLINIC_ADMIN")))
      .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
      .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    var json = mapper.readTree(body);
    assertThat(json.size()).isEqualTo(3);
    String[] codes = {"BASIC", "PROFESSIONAL", "ENTERPRISE"};
    String[] names = {"Básico", "Profesional", "Enterprise"};
    String[] amounts = {"39.90", "49.99", "59.99"};
    for (int i = 0; i < 3; i++) {
      var plan = json.get(i);
      assertThat(plan.size()).isEqualTo(6);
      var quota = plan.get("quota");
      if (i == 2) {
        assertThat(quota.get("sites").isNull()).isTrue();
        assertThat(quota.get("floors").isNull()).isTrue();
        assertThat(quota.get("beacons").isNull()).isTrue();
      } else {
        assertThat(quota.get("sites").asInt()).isEqualTo(i == 0 ? 1 : 3);
        assertThat(quota.get("floors").asInt()).isEqualTo(i == 0 ? 3 : 10);
        assertThat(quota.get("beacons").asInt()).isEqualTo(i == 0 ? 15 : 50);
      }
      assertThat(plan.get("code").asText()).isEqualTo(codes[i]);
      assertThat(plan.get("name").asText()).isEqualTo(names[i]);
      assertThat(plan.get("price").size()).isEqualTo(2);
      assertThat(plan.at("/price/amount").isNumber()).isTrue();
      assertThat(plan.at("/price/amount").decimalValue()).isEqualByComparingTo(new BigDecimal(amounts[i]));
      assertThat(body).contains("\"amount\":" + amounts[i]);
      assertThat(plan.at("/price/currency").asText()).isEqualTo("PEN");
      assertThat(plan.get("billingInterval").asText()).isEqualTo("MONTH");
      assertThat(plan.get("purchaseMode").asText()).isEqualTo(i == 2 ? "CONTACT_SALES" : "CHECKOUT");
    }
    assertThat(plans.findByCode(PlanCode.BASIC)).hasValueSatisfying(plan ->
      assertThat(plan.price().amount()).isEqualTo(new BigDecimal("39.90")));
  }

  @Test
  void patientIsForbidden() throws Exception {
    mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token("PATIENT")))
      .andExpect(status().isForbidden());
  }

  @Test
  void anonymousIsUnauthorized() throws Exception {
    mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
  }

  @Test
  @Transactional
  void persistenceExcludesInactivePlansAndEndpointUsesDisplayOrder() throws Exception {
    String admin = token("CLINIC_ADMIN");
    jdbc.update("UPDATE subscriptions_plans SET active = false WHERE code = 'PROFESSIONAL'");
    jdbc.update("UPDATE subscriptions_plans SET display_order = 0 WHERE code = 'ENTERPRISE'");
    assertThat(plans.findAllActive()).extracting(plan -> plan.code()).containsExactlyInAnyOrder(PlanCode.BASIC, PlanCode.ENTERPRISE);
    mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
      .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
      .andExpect(jsonPath("$[0].code").value("ENTERPRISE"))
      .andExpect(jsonPath("$[1].code").value("BASIC"));
  }

  @Test
  void schemaHasTheRequiredColumnsTypesAndNullability() {
    List<String> columns = jdbc.query("""
      SELECT column_name || ':' || data_type || ':' || is_nullable
      FROM information_schema.columns
      WHERE table_schema = 'public' AND table_name = 'subscriptions_plans'
      ORDER BY ordinal_position
      """, (rs, row) -> rs.getString(1));
    assertThat(columns).containsExactly("id:uuid:NO", "code:character varying:NO", "name:character varying:NO",
      "price_amount:numeric:NO", "currency:character:NO", "billing_interval:character varying:NO",
      "purchase_mode:character varying:NO", "display_order:integer:NO", "active:boolean:NO",
      "created_at:timestamp with time zone:YES", "updated_at:timestamp with time zone:YES");
    assertThat(jdbc.query("""
      SELECT column_name || ':' || character_maximum_length
      FROM information_schema.columns
      WHERE table_schema = 'public' AND table_name = 'subscriptions_plans'
        AND character_maximum_length IS NOT NULL
      ORDER BY ordinal_position
      """, (rs, row) -> rs.getString(1))).containsExactly(
        "code:30", "name:60", "currency:3", "billing_interval:10", "purchase_mode:20");
    assertThat(jdbc.queryForObject("""
      SELECT numeric_precision = 10 AND numeric_scale = 2 FROM information_schema.columns
      WHERE table_schema = 'public' AND table_name = 'subscriptions_plans' AND column_name = 'price_amount'
      """, Boolean.class)).isTrue();
  }

  private String token(String role) throws Exception {
    String email = "catalog-" + UUID.randomUUID() + "@example.test";
    String password = "Test-" + UUID.randomUUID();
    mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON)
      .content(mapper.writeValueAsString(java.util.Map.of("email", email, "password", password,
        "role", role, "fullName", "Catalog Tester", "clinicName", "Catalog Clinic", "acceptedTerms", true))))
      .andExpect(status().isCreated());
    String response = mvc.perform(post("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
      .content(mapper.writeValueAsString(java.util.Map.of("email", email, "password", password))))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return mapper.readTree(response).get("accessToken").asText();
  }
}
