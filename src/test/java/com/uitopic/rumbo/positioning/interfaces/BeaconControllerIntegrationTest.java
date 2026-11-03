package com.uitopic.rumbo.positioning.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
import com.uitopic.rumbo.iam.domain.model.valueobjects.UserRole;
import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class BeaconControllerIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

  private RequestPostProcessor caller(UUID userId, UserRole role) {
    var principal = new AuthenticatedPrincipal(userId, role, UUID.randomUUID(), null);
    var auth = new UsernamePasswordAuthenticationToken(principal, null,
      List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    return SecurityMockMvcRequestPostProcessors.authentication(auth);
  }

  private UUID site(UUID user) throws Exception {
    // A real row is needed because indoor_sites enforces an FK to identity_users.
    jdbc.update("""
        INSERT INTO identity_users (id, email, password_hash, role, status, created_at, updated_at)
        VALUES (?, ?, ?, 'CLINIC_ADMIN', 'ACTIVE', now(), now())
        """, user, "beacons-" + user + "@example.com", "test-only-not-a-real-password-hash");
    String payload = mvc.perform(post("/api/v1/sites").with(caller(user, UserRole.CLINIC_ADMIN))
        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Centro Medico\"}"))
      .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return UUID.fromString(json.readTree(payload).get("id").asText());
  }

  @Test void adminRegistersAndListsByFloorAndTogglesStatus() throws Exception {
    UUID admin = UUID.randomUUID(), siteId = site(admin), floor = UUID.randomUUID();
    String payload = mvc.perform(post("/api/v1/sites/" + siteId + "/beacons")
        .with(caller(admin, UserRole.CLINIC_ADMIN)).contentType(MediaType.APPLICATION_JSON)
        .content("{\"identifier\":\" AA:BB:CC:DD \",\"floorId\":\"" + floor + "\",\"x\":10.0,\"y\":5.5}"))
      .andExpect(status().isCreated()).andExpect(jsonPath("$.identifier").value("aa:bb:cc:dd"))
      .andReturn().getResponse().getContentAsString();
    UUID beaconId = UUID.fromString(json.readTree(payload).get("id").asText());
    mvc.perform(get("/api/v1/sites/" + siteId + "/beacons").with(caller(admin, UserRole.CLINIC_ADMIN))
        .param("floorId", floor.toString()))
      .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(beaconId.toString()));
    mvc.perform(patch("/api/v1/sites/" + siteId + "/beacons/" + beaconId + "/status")
        .with(caller(admin, UserRole.CLINIC_ADMIN)).contentType(MediaType.APPLICATION_JSON)
        .content("{\"active\":false}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
  }

  @Test void otherAdminCannotSeeOrModifyTheSite() throws Exception {
    UUID owner = UUID.randomUUID(), siteId = site(owner), stranger = UUID.randomUUID();
    mvc.perform(get("/api/v1/sites/" + siteId + "/beacons")
        .with(caller(stranger, UserRole.CLINIC_ADMIN)))
      .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POSITIONING_RESOURCE_NOT_FOUND"));
    mvc.perform(post("/api/v1/sites/" + siteId + "/beacons")
        .with(caller(stranger, UserRole.CLINIC_ADMIN)).contentType(MediaType.APPLICATION_JSON)
        .content("{\"identifier\":\"aa:bb:cc\",\"floorId\":\"" + UUID.randomUUID() + "\",\"x\":1,\"y\":2}"))
      .andExpect(status().isNotFound());
  }

  @Test void patientIsForbiddenAndAnonymousIsUnauthorized() throws Exception {
    mvc.perform(post("/api/v1/sites").with(caller(UUID.randomUUID(), UserRole.PATIENT))
        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Test\"}"))
      .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/sites")).andExpect(status().isUnauthorized());
  }

  @Test void duplicateIdentifierCannotBeRegisteredTwice() throws Exception {
    UUID admin = UUID.randomUUID(), siteId = site(admin), floor = UUID.randomUUID();
    String payload = "{\"identifier\":\"aa:bb:cc\",\"floorId\":\"" + floor + "\",\"x\":1,\"y\":2}";
    mvc.perform(post("/api/v1/sites/" + siteId + "/beacons").with(caller(admin, UserRole.CLINIC_ADMIN))
        .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isCreated());
    mvc.perform(post("/api/v1/sites/" + siteId + "/beacons").with(caller(admin, UserRole.CLINIC_ADMIN))
        .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("BEACON_IDENTIFIER_EXISTS"));
  }
  @Test void listByFloorReturnsOnlyThatFloorEvenWhenOtherFloorsHaveInventory() throws Exception {
    UUID admin = UUID.randomUUID(), siteId = site(admin);
    UUID floorA = UUID.randomUUID(), floorB = UUID.randomUUID();
    String base = "/api/v1/sites/" + siteId + "/beacons";
    mvc.perform(post(base).with(caller(admin, UserRole.CLINIC_ADMIN))
       .contentType(MediaType.APPLICATION_JSON)
       .content("{\"identifier\":\"aaaa1234\",\"floorId\":\"" + floorA + "\",\"x\":10,\"y\":5}"))
      .andExpect(status().isCreated());
    mvc.perform(post(base).with(caller(admin, UserRole.CLINIC_ADMIN))
       .contentType(MediaType.APPLICATION_JSON)
       .content("{\"identifier\":\"bbbb5678\",\"floorId\":\"" + floorB + "\",\"x\":2,\"y\":1}"))
      .andExpect(status().isCreated());

    mvc.perform(get(base).with(caller(admin, UserRole.CLINIC_ADMIN)).param("floorId", floorA.toString()))
      .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
      .andExpect(jsonPath("$[0].identifier").value("aaaa1234"))
      .andExpect(jsonPath("$[0].floorId").value(floorA.toString()));

    mvc.perform(get(base).with(caller(admin, UserRole.CLINIC_ADMIN)).param("floorId", UUID.randomUUID().toString()))
      .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    mvc.perform(get(base).with(caller(admin, UserRole.CLINIC_ADMIN)))
      .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
  }

  @Test void rejectsInvalidCoordinatesAndUnknownBeaconWithoutLeakingSiteData() throws Exception {
    UUID admin = UUID.randomUUID(), siteId = site(admin), floor = UUID.randomUUID();
    String base = "/api/v1/sites/" + siteId + "/beacons";
    mvc.perform(post(base).with(caller(admin, UserRole.CLINIC_ADMIN))
       .contentType(MediaType.APPLICATION_JSON)
       .content("{\"identifier\":\"test1234\",\"floorId\":\"" + floor + "\",\"x\":-2,\"y\":1}"))
      .andExpect(status().isUnprocessableEntity())
      .andExpect(jsonPath("$.code").value("INVALID_BEACON"));
    mvc.perform(get(base + "/" + UUID.randomUUID()).with(caller(admin, UserRole.CLINIC_ADMIN)))
      .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POSITIONING_RESOURCE_NOT_FOUND"));
    mvc.perform(get(base).with(caller(admin, UserRole.CLINIC_ADMIN)).param("floorId", "not-a-uuid"))
      .andExpect(status().isBadRequest());
    mvc.perform(get(base).with(caller(admin, UserRole.CLINIC_ADMIN)))
      .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
  }

}
