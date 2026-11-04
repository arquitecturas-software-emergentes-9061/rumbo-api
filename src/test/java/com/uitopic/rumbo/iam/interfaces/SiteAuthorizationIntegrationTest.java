package com.uitopic.rumbo.iam.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TS-24 evidence: administrative operations on plans, graphs and beacons are bound to the site of the
 * authenticated account. Runs the real chain (sign-up, sign-in, JWT filter, method security) on PostgreSQL in a
 * container with the {@code postgres} profile, so it also covers the Planning endpoints whose own tests need an
 * external database.
 * <p>Cases from the card: authorized operation, anonymous request and administrator of another site, without
 * changes being applied. Patients are also rejected.
 */
@SpringBootTest(properties = {"rumbo.planning.editing.enabled=true", "rumbo.planning.publication.enabled=true"})
@ActiveProfiles("postgres")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class SiteAuthorizationIntegrationTest {
  private static final String PASSWORD = "Rumbo2026";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;

  private String owner;
  private String otherAdmin;
  private String patient;
  private UUID siteId;
  private UUID mapId;
  private UUID versionId;
  private UUID floorId;

  @BeforeEach
  void ownerWithADraftMap() throws Exception {
    owner = tokenOf(register("CLINIC_ADMIN"));
    otherAdmin = tokenOf(register("CLINIC_ADMIN"));
    patient = tokenOf(register("PATIENT"));
    siteId = UUID.fromString(mapper.readTree(mvc.perform(post("/api/v1/sites").header(HttpHeaders.AUTHORIZATION,
        bearer(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sede Central\"}"))
      .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText());
    mapId = UUID.randomUUID();
    versionId = UUID.randomUUID();
    floorId = UUID.randomUUID();
    jdbc.update("INSERT INTO indoor_maps (id, site_id) VALUES (?, ?)", mapId, siteId);
    jdbc.update("INSERT INTO map_versions (id, map_id, status) VALUES (?, ?, 'DRAFT')", versionId, mapId);
    jdbc.update("""
      INSERT INTO plan_coordinate_spaces (plan_version_id, model_version_id, floor_id, width_units, height_units)
      VALUES (?, ?, ?, 100, 100)""", UUID.randomUUID(), versionId, floorId);
  }

  @Test
  void anonymousRequestsAreRejectedOnEveryResource() throws Exception {
    mvc.perform(graph()).andExpect(status().isUnauthorized());
    mvc.perform(publish()).andExpect(status().isUnauthorized());
    mvc.perform(beacons()).andExpect(status().isUnauthorized());
    mvc.perform(registerBeacon()).andExpect(status().isUnauthorized());
    mvc.perform(calibration()).andExpect(status().isUnauthorized());
  }

  @Test
  void patientsCannotRunAdministrativeOperations() throws Exception {
    mvc.perform(as(patient, graph())).andExpect(status().isForbidden());
    mvc.perform(as(patient, publish())).andExpect(status().isForbidden());
    mvc.perform(as(patient, beacons())).andExpect(status().isForbidden());
    mvc.perform(as(patient, calibration())).andExpect(status().isForbidden());
  }

  @Test
  void anAdministratorOfAnotherSiteCannotReadOrChangeIt() throws Exception {
    mvc.perform(as(otherAdmin, graph())).andExpect(status().isForbidden());
    mvc.perform(as(otherAdmin, publish())).andExpect(status().isForbidden());
    // Beacon inventory and calibration answer 404 so a foreign site cannot even be confirmed to exist.
    mvc.perform(as(otherAdmin, beacons())).andExpect(status().isNotFound());
    mvc.perform(as(otherAdmin, registerBeacon())).andExpect(status().isNotFound());
    mvc.perform(as(otherAdmin, calibration())).andExpect(status().isNotFound());

    assertThat(jdbc.queryForObject("SELECT status FROM map_versions WHERE id = ?", String.class, versionId))
      .as("the draft was not published").isEqualTo("DRAFT");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM positioning_beacons WHERE site_id = ?", Integer.class,
      siteId)).as("no beacon was registered").isZero();
  }

  @Test
  void theOwnerPassesAuthorizationOnItsSite() throws Exception {
    mvc.perform(as(owner, graph())).andExpect(status().isOk());
    mvc.perform(as(owner, beacons())).andExpect(status().isOk());
    mvc.perform(as(owner, registerBeacon())).andExpect(status().isCreated());
    // The empty draft fails Planning's own validation, which only runs once authorization has passed.
    int publishStatus = mvc.perform(as(owner, publish())).andReturn().getResponse().getStatus();
    assertThat(publishStatus).isNotIn(401, 403);
  }

  private MockHttpServletRequestBuilder graph() {
    return get("/api/v1/sites/{site}/maps/{map}/versions/{version}/graph", siteId, mapId, versionId);
  }

  private MockHttpServletRequestBuilder publish() {
    return patch("/api/v1/sites/{site}/maps/{map}/versions/{version}", siteId, mapId, versionId)
      .contentType("application/merge-patch+json").content("{\"status\":\"PUBLISHED\"}");
  }

  private MockHttpServletRequestBuilder beacons() {
    return get("/api/v1/sites/{site}/beacons", siteId);
  }

  private MockHttpServletRequestBuilder registerBeacon() {
    return post("/api/v1/sites/{site}/beacons", siteId).contentType(MediaType.APPLICATION_JSON)
      .content("{\"identifier\":\"AA:BB:CC:01\",\"floorId\":\"%s\",\"x\":10.0,\"y\":5.0}".formatted(floorId));
  }

  private MockHttpServletRequestBuilder calibration() {
    return get("/api/v1/sites/{site}/plan-calibrations/{plan}", siteId, UUID.randomUUID());
  }

  private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
    return request.header(HttpHeaders.AUTHORIZATION, bearer(token));
  }

  private String register(String role) throws Exception {
    String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@clinica.pe";
    mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON).content("""
        {"email":"%s","password":"%s","role":"%s","fullName":"Ana Torres","clinicName":"Clinica Central",
         "acceptedTerms":true}""".formatted(email, PASSWORD, role)))
      .andExpect(status().isCreated());
    return email;
  }

  private String tokenOf(String email) throws Exception {
    String body = mvc.perform(post("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return mapper.readTree(body).get("accessToken").asText();
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }
}
