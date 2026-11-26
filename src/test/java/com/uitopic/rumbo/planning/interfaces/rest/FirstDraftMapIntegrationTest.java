package com.uitopic.rumbo.planning.interfaces.rest;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A brand-new site has uploaded plans but no map. The first draft is created from those plans, taking dimensions and
 * the confirmed scale from Planning, and from then on the ordinary draft endpoints (read, replace with If-Match) apply.
 */
@SpringBootTest
@ActiveProfiles("postgres")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class FirstDraftMapIntegrationTest {
  private static final String PASSWORD = "PlanningTest2026";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  @Test
  void aNewSiteGetsAnEmptyDraftWithTheStoredPlanBoundsAndConfirmedScale() throws Exception {
    Fixture fixture = fixture();
    calibrate(fixture);

    String body = createDraft(fixture, fixture.floor, fixture.version).andExpect(status().isCreated())
      .andExpect(header().string("ETag", "\"rev-0\""))
      .andExpect(jsonPath("$.status").value("DRAFT"))
      .andExpect(jsonPath("$.siteId").value(fixture.site.toString()))
      .andExpect(jsonPath("$.plans.length()").value(1))
      .andExpect(jsonPath("$.plans[0].floorId").value(fixture.floor.toString()))
      .andExpect(jsonPath("$.plans[0].planVersionId").value(fixture.version.toString()))
      .andExpect(jsonPath("$.plans[0].widthUnits").value(200.0))
      .andExpect(jsonPath("$.plans[0].heightUnits").value(100.0))
      .andExpect(jsonPath("$.plans[0].metersPerUnit").value(0.1))
      .andExpect(jsonPath("$.nodes.length()").value(0))
      .andReturn().getResponse().getContentAsString();
    JsonNode created = json.readTree(body);

    mvc.perform(get("/api/v1/sites/" + fixture.site + "/maps/draft/graph").header(HttpHeaders.AUTHORIZATION, fixture.auth()))
      .andExpect(status().isOk()).andExpect(header().string("ETag", "\"rev-0\""))
      .andExpect(jsonPath("$.modelVersionId").value(created.path("modelVersionId").asText()));
  }

  @Test
  void theNewDraftCanBeSavedWithTheEditorsIfMatchFlow() throws Exception {
    Fixture fixture = fixture();
    calibrate(fixture);
    JsonNode draft = json.readTree(createDraft(fixture, fixture.floor, fixture.version).andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString());
    UUID node = UUID.randomUUID();
    Map<String, Object> graph = Map.of(
      "nodes", List.of(Map.of("id", node, "floorId", fixture.floor, "planVersionId", fixture.version,
        "x", 20.0, "y", 30.0, "type", "ACCESS")),
      "connections", List.of(), "destinations", List.of());
    String url = "/api/v1/sites/" + fixture.site + "/maps/" + draft.path("mapId").asText()
      + "/versions/" + draft.path("modelVersionId").asText() + "/graph";

    mvc.perform(put(url).header(HttpHeaders.AUTHORIZATION, fixture.auth()).header(HttpHeaders.IF_MATCH, "\"rev-0\"")
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(graph)))
      .andExpect(status().isOk()).andExpect(header().string("ETag", "\"rev-1\""))
      .andExpect(jsonPath("$.nodes.length()").value(1));
  }

  @Test
  void aPlanWithoutCalibrationLeavesTheScaleEmpty() throws Exception {
    Fixture fixture = fixture();
    createDraft(fixture, fixture.floor, fixture.version).andExpect(status().isCreated())
      .andExpect(jsonPath("$.plans[0].metersPerUnit").doesNotExist());
  }

  @Test
  void aSecondFirstDraftIsRejectedWhileTheDraftExists() throws Exception {
    Fixture fixture = fixture();
    createDraft(fixture, fixture.floor, fixture.version).andExpect(status().isCreated());
    createDraft(fixture, fixture.floor, fixture.version).andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("DRAFT_ALREADY_EXISTS"));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM indoor_maps WHERE site_id = ?", Integer.class, fixture.site))
      .isEqualTo(1);
  }

  @Test
  void aSiteWithAPublishedMapMustUseTheSuccessorFlow() throws Exception {
    Fixture fixture = fixture();
    UUID map = UUID.randomUUID();
    jdbc.update("INSERT INTO indoor_maps (id, site_id) VALUES (?, ?)", map, fixture.site);
    jdbc.update("INSERT INTO map_versions (id, map_id, status) VALUES (?, ?, 'PUBLISHED')", UUID.randomUUID(), map);

    createDraft(fixture, fixture.floor, fixture.version).andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("SITE_MAP_ALREADY_EXISTS"));
  }

  @Test
  void aPlanOfAnotherFloorOrSiteIsNotFound() throws Exception {
    Fixture fixture = fixture();
    Fixture other = fixture();
    createDraft(fixture, fixture.floor, other.version).andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("PLAN_VERSION_NOT_FOUND"));
    createDraft(fixture, other.floor, fixture.version).andExpect(status().isNotFound());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM indoor_maps WHERE site_id = ?", Integer.class, fixture.site))
      .isZero();
  }

  @Test
  void emptyOrDuplicatedPlansAreBadRequests() throws Exception {
    Fixture fixture = fixture();
    postDraft(fixture, "{\"plans\":[]}").andExpect(status().isBadRequest());
    postDraft(fixture, "{}").andExpect(status().isBadRequest());
    String entry = "{\"floorId\":\"" + fixture.floor + "\",\"planVersionId\":\"" + fixture.version + "\"}";
    postDraft(fixture, "{\"plans\":[" + entry + "," + entry + "]}").andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void anotherAdministratorCannotCreateTheDraft() throws Exception {
    Fixture fixture = fixture();
    String intruder = "Bearer " + token();
    mvc.perform(post("/api/v1/sites/" + fixture.site + "/maps/draft").header(HttpHeaders.AUTHORIZATION, intruder)
        .contentType(MediaType.APPLICATION_JSON).content(plans(fixture.floor, fixture.version)))
      .andExpect(status().isForbidden());
  }

  private record Fixture(String token, UUID site, UUID floor, UUID version) {
    String auth() { return "Bearer " + token; }
  }

  private ResultActions createDraft(Fixture fixture, UUID floor, UUID version) throws Exception {
    return postDraft(fixture, plans(floor, version));
  }

  private ResultActions postDraft(Fixture fixture, String body) throws Exception {
    return mvc.perform(post("/api/v1/sites/" + fixture.site + "/maps/draft")
      .header(HttpHeaders.AUTHORIZATION, fixture.auth()).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private static String plans(UUID floor, UUID version) {
    return "{\"plans\":[{\"floorId\":\"" + floor + "\",\"planVersionId\":\"" + version + "\"}]}";
  }

  /** 200x100 px plan: two points 100 units apart that measure 10 m give 0.1 m per unit. */
  private void calibrate(Fixture fixture) throws Exception {
    mvc.perform(put("/api/v1/sites/" + fixture.site + "/plan-calibrations/" + fixture.version)
        .header(HttpHeaders.AUTHORIZATION, fixture.auth()).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("floorId", fixture.floor, "unit", "METERS", "measuredDistance", 10.0,
          "startX", 20.0, "startY", 50.0, "endX", 120.0, "endY", 50.0))))
      .andExpect(status().is2xxSuccessful());
  }

  private Fixture fixture() throws Exception {
    String token = token();
    com.uitopic.rumbo.subscriptions.SubscriptionTestSeeds.activeEnterprise(jdbc,
        com.uitopic.rumbo.subscriptions.SubscriptionTestSeeds.ownerOf(token));
    String siteBody = mvc.perform(post("/api/v1/sites").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"First Draft Site\"}"))
      .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    UUID site = UUID.fromString(json.readTree(siteBody).path("id").asText());
    UUID floor = UUID.randomUUID();
    jdbc.update("INSERT INTO planning_floor_sites (floor_id, site_id) VALUES (?, ?)", floor, site);
    String uploaded = mvc.perform(multipart("/api/v1/sites/" + site + "/floors/" + floor + "/plans")
        .file(new MockMultipartFile("file", "plan.png", "image/png", png(200, 100)))
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
      .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return new Fixture(token, site, floor, UUID.fromString(json.readTree(uploaded).path("id").asText()));
  }

  private String token() throws Exception {
    String email = "first-draft-" + UUID.randomUUID() + "@example.test";
    mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "role", "CLINIC_ADMIN",
          "fullName", "Plan Test", "clinicName", "Test Clinic", "acceptedTerms", true))))
      .andExpect(status().isCreated());
    String session = mvc.perform(post("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return json.readTree(session).path("accessToken").asText();
  }

  private static byte[] png(int width, int height) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, width, height);
    graphics.setColor(Color.BLACK);
    graphics.drawLine(10, height / 2, width - 10, height / 2);
    graphics.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }
}
