package com.uitopic.rumbo.planning.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("postgres")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class ClinicStructureIntegrationTest {
  private static final String CLINIC = "/api/v1/clinics/me";
  private static final String SITES = "/api/v1/sites";
  private static final String PROFILE = """
      {"name":"Clínica de prueba","code":"TEST","contactEmail":"contact@example.test"}
      """;
  private static final String FLOOR = """
      {"number":-1,"name":"Sótano","description":"Prueba de nivel negativo"}
      """;

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  @Test
  void preservesLegacySiteAndPersistsClinicAddressAndFloors() throws Exception {
    String owner = token("CLINIC_ADMIN");
    String site = createSite(owner, "{\"name\":\"Sede original\"}");
    mvc.perform(as(owner, get(CLINIC))).andExpect(status().isNotFound());
    String clinic = id(mvc.perform(as(owner, put(CLINIC)).contentType(MediaType.APPLICATION_JSON).content(PROFILE))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    mvc.perform(as(owner, get(CLINIC))).andExpect(jsonPath("$.id").value(clinic));
    mvc.perform(as(owner, get(SITES))).andExpect(jsonPath("$[0].clinicId").value(clinic));
    mvc.perform(as(owner, put(SITES + "/" + site)).contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"Sede editada\",\"address\":\" Av. Central 500 \"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.address").value("Av. Central 500"));

    String floors = SITES + "/" + site + "/floors";
    String floor = id(mvc.perform(as(owner, post(floors)).contentType(MediaType.APPLICATION_JSON).content(FLOOR))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    mvc.perform(as(owner, get(floors))).andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(floor)).andExpect(jsonPath("$[0].number").value(-1));
    mvc.perform(as(owner, post(floors)).contentType(MediaType.APPLICATION_JSON).content(FLOOR))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FLOOR_NUMBER_EXISTS"));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM planning_floor_sites WHERE site_id = ?",
        Integer.class, UUID.fromString(site))).isEqualTo(1);
  }

  @Test
  void warnsOnMatchingAddressesAndHonorsExplicitAcknowledgement() throws Exception {
    String owner = token("CLINIC_ADMIN");
    createSite(owner, "{\"name\":\"Primera\",\"address\":\"Av. Central 500\"}");
    mvc.perform(as(owner, post(SITES)).contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"Segunda\",\"address\":\" av.   CENTRAL 500 \"}"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SITE_ADDRESS_EXISTS"));
    createSite(owner, "{\"name\":\"Segunda\",\"address\":\"Av. Central 500\",\"allowDuplicateAddress\":true}");
    mvc.perform(as(owner, get(SITES))).andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void rejectsUnauthorizedOperationsAndFractionalFloorNumbers() throws Exception {
    String owner = token("CLINIC_ADMIN");
    String other = token("CLINIC_ADMIN");
    String patient = token("PATIENT");
    String site = createSite(owner, "{\"name\":\"Sede privada\"}");
    String floors = SITES + "/" + site + "/floors";
    mvc.perform(get(floors)).andExpect(status().isUnauthorized());
    mvc.perform(as(patient, get(floors))).andExpect(status().isForbidden());
    mvc.perform(as(other, get(floors))).andExpect(status().isNotFound());
    mvc.perform(as(other, post(floors)).contentType(MediaType.APPLICATION_JSON).content(FLOOR))
        .andExpect(status().isNotFound());
    mvc.perform(as(other, put(SITES + "/" + site)).contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"Cambio ajeno\"}")).andExpect(status().isNotFound());
    mvc.perform(as(owner, post(floors)).contentType(MediaType.APPLICATION_JSON)
        .content("{\"number\":1.5,\"name\":\"Inválido\"}")).andExpect(status().isBadRequest());
    mvc.perform(as(owner, get(floors))).andExpect(jsonPath("$.length()").value(0));
    mvc.perform(as(owner, get(SITES))).andExpect(jsonPath("$[0].name").value("Sede privada"));
  }

  @Test
  void exposesPlanningOperationsWithBearerSecurityInOpenApi() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/clinics/me'].put.security[0].bearerAuth").isArray())
        .andExpect(jsonPath("$.paths['/api/v1/sites/{siteId}/floors'].post.security[0].bearerAuth").isArray());
  }

  private String createSite(String token, String body) throws Exception {
    return id(mvc.perform(as(token, post(SITES)).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
  }

  private String token(String role) throws Exception {
    String email = UUID.randomUUID() + "@example.test";
    String password = "PlanningTest2026";
    mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON).content(
        json.writeValueAsString(Map.of("email", email, "password", password, "role", role,
            "fullName", "Planning Test", "clinicName", "Planning Clinic", "acceptedTerms", true))))
        .andExpect(status().isCreated());
    String body = mvc.perform(post("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return json.readTree(body).get("accessToken").asText();
  }

  private String id(String body) throws Exception {
    return json.readTree(body).get("id").asText();
  }

  private MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
    return request.header("Authorization", "Bearer " + token);
  }
}
