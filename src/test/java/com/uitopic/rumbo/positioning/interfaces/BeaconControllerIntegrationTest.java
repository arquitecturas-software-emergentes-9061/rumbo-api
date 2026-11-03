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

@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class BeaconControllerIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  private RequestPostProcessor caller(UUID userId, UserRole role) {
    var principal = new AuthenticatedPrincipal(userId, role, UUID.randomUUID(), null);
    var auth = new UsernamePasswordAuthenticationToken(principal, null,
      List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    return SecurityMockMvcRequestPostProcessors.authentication(auth);
  }

  private UUID site(UUID user) throws Exception {
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
}
