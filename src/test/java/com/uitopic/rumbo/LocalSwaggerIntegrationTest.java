package com.uitopic.rumbo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Verifies that local Swagger exposes live PostgreSQL and isolated demo APIs together. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUMBO_TEST_DB_URL", matches = ".+")
class LocalSwaggerIntegrationTest {
  private static final String PUBLISHED_SITE = "10000000-0000-4000-8000-000000000001";
  private static final String DRAFT_SITE = "20000000-0000-4000-8000-000000000001";
  private static final String DEMO_SITE = "00000000-0000-4000-8000-000000000001";

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", () -> System.getenv("RUMBO_TEST_DB_URL"));
    properties.add("spring.datasource.username", () -> System.getenv("RUMBO_TEST_DB_USER"));
    properties.add("spring.datasource.password", () -> System.getenv("RUMBO_TEST_DB_PASSWORD"));
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void allSevenOperationsAreDocumentedAndLocalExamplesRespond() throws Exception {
    JsonNode paths = json.readTree(mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("paths");
    String graph = "/api/v1/sites/{siteId}/maps/{mapId}/versions/{versionId}/graph";
    assertTrue(paths.path(graph).has("get"));
    assertTrue(paths.path(graph).has("put"));
    assertTrue(paths.path("/api/v1/sites/{siteId}/maps/{mapId}/versions/{versionId}").has("patch"));
    assertTrue(paths.path("/api/v1/sites/{siteId}/directory-entries").has("get"));
    assertTrue(paths.path("/api/v1/sites/{siteId}/routes").has("get"));
    assertTrue(paths.path("/api/v1/navigations/destinations").has("get"));
    assertTrue(paths.path("/api/v1/navigations/route").has("post"));
    assertEquals("Indoor map graph", paths.path(graph).path("get").path("tags").path(0).asText());
    assertEquals("Indoor maps", paths.path("/api/v1/sites/{siteId}/maps/{mapId}/versions/{versionId}")
        .path("patch").path("tags").path(0).asText());
    assertEquals("Directory", paths.path("/api/v1/sites/{siteId}/directory-entries")
        .path("get").path("tags").path(0).asText());
    assertEquals("Published routes", paths.path("/api/v1/sites/{siteId}/routes")
        .path("get").path("tags").path(0).asText());
    assertEquals("Demo navigation", paths.path("/api/v1/navigations/route")
        .path("post").path("tags").path(0).asText());

    mvc.perform(get("/api/v1/sites/{siteId}/routes", PUBLISHED_SITE)
            .param("from", "10000000-0000-4000-8000-000000000006")
            .param("to", "10000000-0000-4000-8000-000000000007"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.nodes.length()").value(2));
    mvc.perform(get("/api/v1/sites/{siteId}/directory-entries", PUBLISHED_SITE)
            .param("q", "cardio"))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Cardiologia"));
    mvc.perform(get("/api/v1/sites/{siteId}/maps/{mapId}/versions/{versionId}/graph",
            DRAFT_SITE, "20000000-0000-4000-8000-000000000002",
            "20000000-0000-4000-8000-000000000003"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.nodes.length()").value(2));
    mvc.perform(get("/api/v1/navigations/destinations")
            .param("siteId", DEMO_SITE).param("query", "cardiologia"))
        .andExpect(status().isOk());
    mvc.perform(post("/api/v1/navigations/route").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"siteId":"00000000-0000-4000-8000-000000000001",
                 "startFloorId":"00000000-0000-4000-8000-000000000002",
                 "startNodeId":"00000000-0000-4000-8000-000000000010",
                 "destinationFloorId":"00000000-0000-4000-8000-000000000002",
                 "destinationNodeId":"00000000-0000-4000-8000-000000000011",
                 "accessibleOnly":true}
                """))
        .andExpect(status().isOk());
  }
}
