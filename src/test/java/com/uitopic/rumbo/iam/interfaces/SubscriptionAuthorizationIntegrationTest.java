package com.uitopic.rumbo.iam.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
import com.uitopic.rumbo.iam.application.ports.SubscriptionStatusReader;
import com.uitopic.rumbo.iam.application.ports.SubscriptionStatusReader.Standing;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * TS-28 evidence (WI-TS28-02, and the expiry cases of WI-TS28-05): with the real security chain on PostgreSQL, an
 * administrator whose plan is not active cannot change its sites, but still reads them and can register its clinic.
 * Subscriptions is replaced by a stub of {@link SubscriptionStatusReader}, the port its adapter will implement.
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
  @MockitoBean SubscriptionStatusReader subscriptions;

  private String admin;

  @BeforeEach
  void administrator() throws Exception {
    admin = tokenOf(register());
  }

  @Test
  void anActivePlanLetsTheAdministratorCreateASite() throws Exception {
    when(subscriptions.standingOf(any())).thenReturn(Standing.ACTIVE);

    mvc.perform(as(createSite("Sede Activa"))).andExpect(status().isCreated());
  }

  @Test
  void anExpiredPlanBlocksWritesWithItsOwnCodeAndChangesNothing() throws Exception {
    when(subscriptions.standingOf(any())).thenReturn(Standing.EXPIRED);

    mvc.perform(as(createSite("Sede Vencida")))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("SUBSCRIPTION_INACTIVE"))
      .andExpect(jsonPath("$.standing").value("EXPIRED"));

    assertThat(jdbc.queryForObject("SELECT count(*) FROM indoor_sites WHERE name = ?", Integer.class, "Sede Vencida"))
      .as("the site was not created").isZero();
  }

  @Test
  void aClinicThatNeverSubscribedIsAskedToChooseAPlan() throws Exception {
    when(subscriptions.standingOf(any())).thenReturn(Standing.NONE);

    mvc.perform(as(createSite("Sede Nueva")))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("SUBSCRIPTION_INACTIVE"))
      .andExpect(jsonPath("$.standing").value("NONE"));
  }

  @Test
  void readsStayOpenAndTheClinicCanStillBeRegistered() throws Exception {
    when(subscriptions.standingOf(any())).thenReturn(Standing.EXPIRED);

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
