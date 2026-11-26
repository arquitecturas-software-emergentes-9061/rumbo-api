package com.uitopic.rumbo.iam.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PR-4: the landing stores demo and onboarding requests without an account, in a real PostgreSQL. */
@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class OnboardingRequestsIntegrationTest {
  private static final String LANDING_ORIGIN = "http://localhost:5500";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;

  @Test
  void anAnonymousDemoRequestIsStoredAndAnsweredWithItsId() throws Exception {
    String email = "carmen-" + UUID.randomUUID() + "@clinica.pe";
    String body = mvc.perform(post("/api/v1/onboarding-requests").contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.ORIGIN, LANDING_ORIGIN)
        .content(submission("DEMO", email, "TWO_TO_THREE", true)))
      .andExpect(status().isCreated())
      .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LANDING_ORIGIN))
      .andExpect(jsonPath("$.type").value("DEMO"))
      .andExpect(jsonPath("$.email").doesNotExist())
      .andReturn().getResponse().getContentAsString();

    JsonNode json = mapper.readTree(body);
    Map<String, Object> row = jdbc.queryForMap(
      "SELECT request_type, email, clinic_name, sites_range, consent_given_at FROM identity_onboarding_requests "
        + "WHERE id = ?", UUID.fromString(json.get("id").asText()));
    assertThat(row.get("request_type")).isEqualTo("DEMO");
    assertThat(row.get("email")).isEqualTo(email);
    assertThat(row.get("clinic_name")).isEqualTo("Clinica San Lucas");
    assertThat(row.get("sites_range")).isEqualTo("TWO_TO_THREE");
    assertThat(row.get("consent_given_at")).isNotNull();
  }

  @Test
  void theSiteCountAndMessageAreOptional() throws Exception {
    mvc.perform(post("/api/v1/onboarding-requests").contentType(MediaType.APPLICATION_JSON)
        .content(submission("ONBOARDING", "jorge-" + UUID.randomUUID() + "@clinica.pe", null, true)))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.type").value("ONBOARDING"));
  }

  @Test
  void withoutConsentNothingIsStored() throws Exception {
    String email = "sin-consentimiento-" + UUID.randomUUID() + "@clinica.pe";
    mvc.perform(post("/api/v1/onboarding-requests").contentType(MediaType.APPLICATION_JSON)
        .content(submission("DEMO", email, "ONE", false)))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
      .andExpect(jsonPath("$.errors.privacyConsent").exists());

    assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_onboarding_requests WHERE email = ?",
      Integer.class, email)).isZero();
  }

  @Test
  void invalidFieldsAreReportedOneByOne() throws Exception {
    mvc.perform(post("/api/v1/onboarding-requests").contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"type":"DEMO","fullName":" ","email":"not-an-email","clinicName":"Clinica San Lucas",
           "role":"TI","privacyConsent":true}"""))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors.fullName").exists())
      .andExpect(jsonPath("$.errors.email").exists());

    mvc.perform(post("/api/v1/onboarding-requests").contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"type":"CALLBACK","fullName":"Carmen","email":"carmen@clinica.pe","clinicName":"Clinica San Lucas",
           "role":"TI","privacyConsent":true}"""))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }

  @Test
  void theLandingOriginPassesThePreflight() throws Exception {
    mvc.perform(options("/api/v1/onboarding-requests")
        .header(HttpHeaders.ORIGIN, LANDING_ORIGIN)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
      .andExpect(status().isOk())
      .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LANDING_ORIGIN));

    mvc.perform(options("/api/v1/onboarding-requests")
        .header(HttpHeaders.ORIGIN, "https://unknown.example")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
      .andExpect(status().isForbidden());
  }

  private static String submission(String type, String email, String sitesRange, boolean consent) {
    String sites = sitesRange == null ? "" : "\"sitesRange\":\"%s\",".formatted(sitesRange);
    return """
      {"type":"%s","fullName":"Carmen Mendoza","email":"%s","clinicName":"Clinica San Lucas",
       "role":"Directora de TI",%s"message":"Queremos probar Rumbo en dos sedes","privacyConsent":%s}"""
      .formatted(type, email, sites, consent);
  }
}
