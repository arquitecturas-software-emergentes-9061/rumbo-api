package com.uitopic.rumbo.iam.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
import com.uitopic.rumbo.subscriptions.SubscriptionTestSeeds;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * TS-28 evidence (WI-TS28-02, and the expiry cases of WI-TS28-05) with the real security chain, the real
 * Subscriptions module and PostgreSQL. Creating sites, floors and beacons is decided by the quota guard of Planning
 * (409); every other administrative write on a site is decided here (403). Both answer {@code SUBSCRIPTION_REQUIRED}.
 */
@SpringBootTest
@ActiveProfiles("postgres")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class SubscriptionAuthorizationIntegrationTest {
  private static final String PASSWORD = "Rumbo2026";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;

  private String admin;
  private UUID owner;

  @BeforeEach
  void administrator() throws Exception {
    admin = tokenOf(register());
    owner = SubscriptionTestSeeds.ownerOf(admin);
  }

  @Test
  void anActivePlanLetsTheAdministratorCreateAndEditASite() throws Exception {
    SubscriptionTestSeeds.subscription(jdbc, owner, "BASIC", false);
    String site = createdSite("Sede Activa");

    mvc.perform(as(renameSite(site, "Sede Activa Renombrada"))).andExpect(status().isOk());
  }

  @Test
  void anExpiredPlanBlocksEditsWithItsOwnCodeAndChangesNothing() throws Exception {
    SubscriptionTestSeeds.subscription(jdbc, owner, "BASIC", false);
    String site = createdSite("Sede Vencida");
    SubscriptionTestSeeds.subscription(jdbc, owner, "BASIC", true);

    mvc.perform(as(renameSite(site, "Cambio no permitido")))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("SUBSCRIPTION_REQUIRED"))
      .andExpect(jsonPath("$.standing").value("EXPIRED"));

    assertThat(jdbc.queryForObject("SELECT name FROM indoor_sites WHERE id = ?", String.class, UUID.fromString(site)))
      .as("the site keeps its name").isEqualTo("Sede Vencida");
  }

  @Test
  void aCancelledPlanIsTreatedAsExpired() throws Exception {
    SubscriptionTestSeeds.subscription(jdbc, owner, "BASIC", false);
    String site = createdSite("Sede Cancelada");
    jdbc.update("UPDATE subscriptions_subscriptions SET status = 'CANCELLED' WHERE owner_user_id = ?", owner);

    mvc.perform(as(renameSite(site, "Cambio no permitido")))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("SUBSCRIPTION_REQUIRED"));
  }

  @Test
  void creationWithoutAPlanIsLeftToTheQuotaGuard() throws Exception {
    mvc.perform(as(createSite("Sede Nueva")))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("SUBSCRIPTION_REQUIRED"));
  }

  @Test
  void readsStayOpenAndTheClinicCanStillBeRegistered() throws Exception {
    SubscriptionTestSeeds.subscription(jdbc, owner, "BASIC", true);

    mvc.perform(as(get("/api/v1/sites"))).andExpect(status().isOk());
    String code = "CC-" + UUID.randomUUID().toString().substring(0, 8);
    mvc.perform(as(put("/api/v1/clinics/me").contentType(MediaType.APPLICATION_JSON).content(
        "{\"name\":\"Clinica Central\",\"code\":\"%s\",\"contactEmail\":\"contacto@clinica.pe\"}".formatted(code))))
      .andExpect(status().isOk());
  }

  @Test
  void anonymousWritesStillAskForAToken() throws Exception {
    mvc.perform(createSite("Sede Anonima")).andExpect(status().isUnauthorized());
  }

  private String createdSite(String name) throws Exception {
    String body = mvc.perform(as(createSite(name))).andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString();
    return mapper.readTree(body).get("id").asText();
  }

  private MockHttpServletRequestBuilder renameSite(String site, String name) {
    return put("/api/v1/sites/{siteId}", site).contentType(MediaType.APPLICATION_JSON)
      .content("{\"name\":\"%s\"}".formatted(name));
  }
  private MockHttpServletRequestBuilder createSite(String name) {
    return post("/api/v1/sites").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"%s\"}".formatted(name));
  }

  private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request) {
    return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin);
  }

  private String register() throws Exception {
    String email = "admin-" + UUID.randomUUID() + "@clinica.pe";
    mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON).content("""
        {"email":"%s","password":"%s","role":"CLINIC_ADMIN","fullName":"Ana Torres","clinicName":"Clinica Central",
         "acceptedTerms":true}""".formatted(email, PASSWORD)))
      .andExpect(status().isCreated());
    return email;
  }

  private String tokenOf(String email) throws Exception {
    String body = mvc.perform(post("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return mapper.readTree(body).get("accessToken").asText();
  }
}
