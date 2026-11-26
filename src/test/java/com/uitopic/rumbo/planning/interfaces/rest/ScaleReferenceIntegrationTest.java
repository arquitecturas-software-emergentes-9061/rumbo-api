package com.uitopic.rumbo.planning.interfaces.rest;

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
import org.springframework.jdbc.core.JdbcTemplate;
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
class ScaleReferenceIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;

  private RequestPostProcessor admin(UUID id) {
    return SecurityMockMvcRequestPostProcessors.authentication(new UsernamePasswordAuthenticationToken(
      new AuthenticatedPrincipal(id, UserRole.CLINIC_ADMIN, UUID.randomUUID(), null), null,
      List.of(new SimpleGrantedAuthority("ROLE_CLINIC_ADMIN"))));
  }
  private UUID site(UUID id) {
    UUID site = UUID.randomUUID();
    jdbc.update("""
      INSERT INTO identity_users (id,email,password_hash,role,status,created_at,updated_at)
      VALUES (?, ?, ?, 'CLINIC_ADMIN', 'ACTIVE', now(), now())
      """, id, "calibration-" + id + "@example.com", "test-only");
    jdbc.update("INSERT INTO indoor_sites (id,owner_user_id,name) VALUES (?,?,?)",site,id,"Test Clinic");
    com.uitopic.rumbo.subscriptions.SubscriptionTestSeeds.activeEnterprise(jdbc, id);
    return site;
  }
  private void draft(UUID siteId, UUID floorId, UUID planVersionId) {
    UUID mapId = UUID.randomUUID(), modelVersionId = UUID.randomUUID();
    jdbc.update("INSERT INTO indoor_maps (id, site_id) VALUES (?, ?)", mapId, siteId);
    jdbc.update("INSERT INTO map_versions (id, map_id, status) VALUES (?, ?, 'DRAFT')", modelVersionId, mapId);
    jdbc.update("""
      INSERT INTO plan_coordinate_spaces
      (plan_version_id, model_version_id, floor_id, width_units, height_units)
      VALUES (?, ?, ?, 100, 100)
      """, planVersionId, modelVersionId, floorId);
  }
  private String body(UUID floor, int centimeters) {
    return "{\"floorId\":\"" + floor + "\",\"unit\":\"CENTIMETERS\",\"measuredDistance\":"+
      centimeters + ",\"startX\":0,\"startY\":0,\"endX\":30,\"endY\":0}";
  }

  @Test void persistsOriginalMeasurementAndComputedFactorByPlanVersion() throws Exception {
    UUID owner = UUID.randomUUID(), siteId = site(owner), floor = UUID.randomUUID(), plan = UUID.randomUUID();
    draft(siteId, floor, plan);
    String url = "/api/v1/sites/" + siteId + "/plan-calibrations/" + plan;
    mvc.perform(put(url).with(admin(owner)).contentType(MediaType.APPLICATION_JSON)
       .content(body(floor, 300)))
      .andExpect(status().isOk()).andExpect(jsonPath("$.metersPerUnit").value(0.1))
      .andExpect(jsonPath("$.measuredDistance").value(300));
    mvc.perform(get(url).with(admin(owner)))
      .andExpect(status().isOk()).andExpect(jsonPath("$.floorId").value(floor.toString()));
    mvc.perform(put(url).with(admin(owner)).contentType(MediaType.APPLICATION_JSON)
       .content(body(floor, 600)))
      .andExpect(status().isOk()).andExpect(jsonPath("$.metersPerUnit").value(0.2));
    Integer count = jdbc.queryForObject("SELECT count(*) FROM planning_scale_reference_measurements WHERE site_id=? AND plan_version_id=?",
      Integer.class, siteId, plan);
    org.junit.jupiter.api.Assertions.assertEquals(1, count);
    Double stored = jdbc.queryForObject("""
      SELECT p.meters_per_unit FROM plan_coordinate_spaces p
      JOIN map_versions v ON p.model_version_id = v.id
      JOIN indoor_maps m ON m.id = v.map_id
      WHERE m.site_id = ? AND p.plan_version_id = ?
      """, Double.class, siteId, plan);
    org.junit.jupiter.api.Assertions.assertEquals(0.2, stored, 1e-9);
  }

  @Test void rejectsSamePlanVersionUnderDifferentFloorAndWrongSiteOwner() throws Exception {
    UUID owner = UUID.randomUUID(), siteId = site(owner), plan = UUID.randomUUID();
    UUID floor = UUID.randomUUID(), other = UUID.randomUUID(), outsider = UUID.randomUUID();
    draft(siteId, floor, plan);
    String url = "/api/v1/sites/" + siteId + "/plan-calibrations/" + plan;
    mvc.perform(put(url).with(admin(owner)).contentType(MediaType.APPLICATION_JSON).content(body(floor, 300)))
      .andExpect(status().isOk());
    mvc.perform(put(url).with(admin(owner)).contentType(MediaType.APPLICATION_JSON).content(body(other, 300)))
      .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CALIBRATION_FLOOR_CONFLICT"));
    mvc.perform(get(url).with(admin(outsider)))
      .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CALIBRATION_NOT_FOUND"));
  }
  @Test void rejectsCalibrationForUnregisteredPlanOrOutOfBoundsPoints() throws Exception {
    UUID owner = UUID.randomUUID(), siteId = site(owner), floor = UUID.randomUUID(), plan = UUID.randomUUID();
    String url = "/api/v1/sites/" + siteId + "/plan-calibrations/" + plan;
    mvc.perform(put(url).with(admin(owner)).contentType(MediaType.APPLICATION_JSON).content(body(floor, 300)))
      .andExpect(status().isNotFound());
    draft(siteId, floor, plan);
    mvc.perform(put(url).with(admin(owner)).contentType(MediaType.APPLICATION_JSON)
      .content("{\"floorId\":\"" + floor + "\",\"unit\":\"METERS\",\"measuredDistance\":3,\"startX\":0,\"startY\":0,\"endX\":1000,\"endY\":0}"))
      .andExpect(status().isUnprocessableEntity());
  }

}
