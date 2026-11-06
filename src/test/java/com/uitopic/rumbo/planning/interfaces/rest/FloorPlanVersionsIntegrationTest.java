package com.uitopic.rumbo.planning.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.TestcontainersConfiguration;
import com.uitopic.rumbo.planning.domain.model.valueobjects.FloorPlanFile;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("postgres")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class FloorPlanVersionsIntegrationTest {
  private static final String PASSWORD = "PlanningTest2026";
  private static final String SVG = "image/svg+xml";
  private static final String PATH = "/api/v1/sites/{siteId}/floors/{floorId}/plans";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  @Test
  void storesEachOriginalUnchangedAndARejectedUploadDoesNotReplaceTheCurrentVersion() throws Exception {
    String owner = token("CLINIC_ADMIN");
    UUID site = site(owner);
    UUID floor = registerFloor(site);
    byte[] first = svg(10);
    byte[] second = svg(20);

    MvcResult firstUpload = upload(owner, site, floor, "Piso 1.svg", SVG, first);
    String firstUrl = firstUpload.getResponse().getHeader(HttpHeaders.LOCATION);
    var firstMetadata = json.readTree(firstUpload.getResponse().getContentAsString());
    assertThat(firstMetadata.path("version").asInt()).isEqualTo(1);
    assertThat(firstMetadata.path("sizeBytes").asInt()).isEqualTo(first.length);
    assertThat(firstMetadata.path("siteId").asText()).isEqualTo(site.toString());
    assertThat(firstMetadata.path("floorId").asText()).isEqualTo(floor.toString());
    assertThat(firstMetadata.path("mediaType").asText()).isEqualTo(SVG);
    assertThat(firstMetadata.path("fileName").asText()).isEqualTo("Piso 1.svg");
    assertThat(firstMetadata.path("createdAt").asText()).isNotBlank();

    MvcResult secondUpload = upload(owner, site, floor, "Piso 1.svg", SVG, second);
    String secondUrl = secondUpload.getResponse().getHeader(HttpHeaders.LOCATION);
    assertThat(secondUrl).isNotNull().isNotEqualTo(firstUrl);
    assertThat(version(secondUpload)).isEqualTo(2);

    mvc.perform(multipart(path(site, floor)).file(file("piso.svg", SVG, new byte[]{1, 2, 3}))
        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
      .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PLAN_INVALID_CONTENT"));
    assertThat(jdbc.queryForObject("""
      SELECT count(*) FROM planning_floor_plan_file_versions WHERE floor_id = ?
      """, Integer.class, floor)).isEqualTo(2);

    mvc.perform(get(firstUrl).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
      .andExpect(status().isOk()).andExpect(content().contentType(SVG)).andExpect(content().bytes(first))
      .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
      .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    mvc.perform(get(secondUrl).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
      .andExpect(status().isOk()).andExpect(content().bytes(second));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM indoor_maps WHERE site_id = ?", Integer.class, site))
      .isZero();
  }

  @Test
  void scopesBothUploadAndDownloadToAnAuthenticatedAdministratorAndExactFloorOwnership() throws Exception {
    String owner = token("CLINIC_ADMIN");
    String outsider = token("CLINIC_ADMIN");
    String patient = token("PATIENT");
    UUID site = site(owner), otherSite = site(owner);
    UUID floor = registerFloor(site), otherFloor = registerFloor(otherSite);
    String url = path(site, floor);
    var valid = file("piso.svg", SVG, svg(10));

    mvc.perform(multipart(url).file(valid)).andExpect(status().isUnauthorized());
    mvc.perform(multipart(url).file(valid).header(HttpHeaders.AUTHORIZATION, bearer(patient)))
      .andExpect(status().isForbidden());
    mvc.perform(multipart(url).file(valid).header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
      .andExpect(status().isNotFound());
    mvc.perform(multipart(path(site, otherFloor)).file(valid).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
      .andExpect(status().isNotFound());
    mvc.perform(multipart(path(site, UUID.randomUUID())).file(valid)
        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
      .andExpect(status().isNotFound());

    MvcResult stored = upload(owner, site, floor, "piso.svg", SVG, svg(10));
    String originalUrl = stored.getResponse().getHeader(HttpHeaders.LOCATION);
    String versionId = json.readTree(stored.getResponse().getContentAsString()).path("id").asText();
    mvc.perform(get(originalUrl)).andExpect(status().isUnauthorized());
    mvc.perform(get(originalUrl).header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
      .andExpect(status().isNotFound());
    mvc.perform(get(path(otherSite, otherFloor) + "/versions/" + versionId)
        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
      .andExpect(status().isNotFound());
  }

  @Test
  void rejectsMissingEmptyUnsupportedAndOversizedPartsWithoutPersistingAFile() throws Exception {
    String owner = token("CLINIC_ADMIN");
    UUID site = site(owner), floor = registerFloor(site);
    String url = path(site, floor);

    mvc.perform(multipart(url).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
      .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLAN_FILE_REQUIRED"));
    mvc.perform(multipart(url).file(file("piso.svg", SVG, new byte[0]))
        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
      .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLAN_EMPTY_FILE"));
    mvc.perform(multipart(url).file(file("piso.dwg", "application/octet-stream", svg(10)))
        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
      .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("PLAN_UNSUPPORTED_TYPE"));
    mvc.perform(multipart(url).file(file("piso.svg", SVG, new byte[FloorPlanFile.MAX_BYTES + 1]))
        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
      .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("PLAN_FILE_TOO_LARGE"));
    assertThat(jdbc.queryForObject("""
      SELECT count(*) FROM planning_floor_plan_file_versions WHERE floor_id = ?
      """, Integer.class, floor)).isZero();
  }

  @Test
  void serializesConcurrentUploadsToTheSameFloor() throws Exception {
    String owner = token("CLINIC_ADMIN");
    UUID site = site(owner), floor = registerFloor(site);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> upload(owner, site, floor, "first.svg", SVG, svg(10)));
      var second = executor.submit(() -> upload(owner, site, floor, "second.svg", SVG, svg(20)));
      assertThat(new int[]{version(first.get(10, TimeUnit.SECONDS)), version(second.get(10, TimeUnit.SECONDS))})
        .containsExactlyInAnyOrder(1, 2);
    }
  }

  @Test
  void listsMetadataNewestFirstAndDistinguishesEmptyUnknownAndUnauthorizedScopes() throws Exception {
    String owner = token("CLINIC_ADMIN");
    UUID site = site(owner), floor = registerFloor(site);
    String path = path(site, floor);
    mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
      .andExpect(status().isOk()).andExpect(content().json("[]"));
    upload(owner, site, floor, "first.svg", SVG, svg(10));
    var last = upload(owner, site, floor, "second.svg", SVG, svg(20));
    String id = json.readTree(last.getResponse().getContentAsString()).path("id").asText();
    mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
      .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id))
      .andExpect(jsonPath("$[0].version").value(2)).andExpect(jsonPath("$[1].version").value(1))
      .andExpect(jsonPath("$[0].fileName").value("second.svg"))
      .andExpect(jsonPath("$[0].sizeBytes").value(svg(20).length))
      .andExpect(jsonPath("$[0].bytes").doesNotExist());
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(token("PATIENT"))))
      .andExpect(status().isForbidden());
    mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(token("CLINIC_ADMIN"))))
      .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SITE_NOT_FOUND"));
    mvc.perform(get(path(UUID.randomUUID(), floor)).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
      .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SITE_NOT_FOUND"));
    mvc.perform(get(path(site, registerFloor(site(owner)))).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
      .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FLOOR_NOT_FOUND"));
    mvc.perform(get(path(site, UUID.randomUUID())).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
      .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FLOOR_NOT_FOUND"));
  }

  @Test
  void exposesMultipartAndBinaryContractsInOpenApi() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
      .andExpect(jsonPath("$.paths['" + PATH + "'].post.requestBody.content['multipart/form-data']"
        + ".schema.properties.file.format").value("binary"))
      .andExpect(jsonPath("$.paths['" + PATH + "'].post.responses['413']").exists())
      .andExpect(jsonPath("$.paths['" + PATH + "'].get.responses['200'].content['application/json'].schema.type")
        .value("array"))
      .andExpect(jsonPath("$.paths['" + PATH + "/versions/{versionId}'].get.responses['200']"
        + ".content['application/octet-stream'].schema.format").value("binary"));
  }

  private MvcResult upload(String token, UUID site, UUID floor, String name, String mime, byte[] bytes)
      throws Exception {
    return mvc.perform(multipart(path(site, floor)).file(file(name, mime, bytes))
        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
      .andExpect(status().isCreated()).andExpect(header().exists(HttpHeaders.LOCATION)).andReturn();
  }

  private int version(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString()).path("version").asInt();
  }

  private String token(String role) throws Exception {
    String email = "floor-plan-" + UUID.randomUUID() + "@example.test";
    mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "role", role,
          "fullName", "Plan Test", "clinicName", "Test Clinic", "acceptedTerms", true))))
      .andExpect(status().isCreated());
    MvcResult result = mvc.perform(post("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
      .andExpect(status().isOk()).andReturn();
    return json.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
  }

  private UUID site(String token) throws Exception {
    var result = mvc.perform(post("/api/v1/sites").header(HttpHeaders.AUTHORIZATION, bearer(token))
        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Plan Test Site\"}"))
      .andExpect(status().isCreated()).andReturn();
    return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).path("id").asText());
  }

  private UUID registerFloor(UUID site) {
    UUID floor = UUID.randomUUID();
    jdbc.update("INSERT INTO planning_floor_sites (floor_id, site_id) VALUES (?, ?)", floor, site);
    return floor;
  }

  private static MockMultipartFile file(String name, String mime, byte[] bytes) {
    return new MockMultipartFile("file", name, mime, bytes);
  }

  private static byte[] svg(int width) {
    return ("<svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"" + width
      + "\" height=\"10\"/></svg>").getBytes(StandardCharsets.UTF_8);
  }

  private static String path(UUID site, UUID floor) {
    return "/api/v1/sites/" + site + "/floors/" + floor + "/plans";
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }
}
