package com.uitopic.rumbo.navigation.interfaces.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import com.uitopic.rumbo.iam.infrastructure.security.SiteOwnerGuard;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.DirectoryEntryType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapScale;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PlanCoordinateSpace;
import com.uitopic.rumbo.planning.domain.model.commands.PublishMapVersionCommand;
import com.uitopic.rumbo.planning.domain.repositories.IndoorMapRepository;
import com.uitopic.rumbo.planning.domain.services.IndoorMapCommandService;
import com.uitopic.rumbo.planning.interfaces.acl.PlanningPublishedMapFacade;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** HTTP search checks against versioned directory entries stored in PostgreSQL. */
@SpringBootTest(properties = "rumbo.navigation.directory.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "RUMBO_TEST_DB_URL", matches = ".+")
@WithMockUser(roles = "CLINIC_ADMIN")
class DirectoryControllerIntegrationTest {
  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", () -> System.getenv("RUMBO_TEST_DB_URL"));
    properties.add("spring.datasource.username", () -> System.getenv("RUMBO_TEST_DB_USER"));
    properties.add("spring.datasource.password", () -> System.getenv("RUMBO_TEST_DB_PASSWORD"));
  }

  @Autowired MockMvc mvc;
  @Autowired IndoorMapRepository maps;
  @Autowired IndoorMapCommandService commands;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlanningPublishedMapFacade planning;
  @MockitoBean SiteOwnerGuard siteOwnerGuard;

  @BeforeEach void allowFixtureSites() {
    when(siteOwnerGuard.isOwner(any(), any())).thenReturn(true);
  }

  @Test
  void searchesOnlyCurrentPublishedEntriesWithAccentInsensitivePrefixes() throws Exception {
    UUID siteId = UUID.randomUUID();
    UUID mapId = UUID.randomUUID();
    IndoorMap first = draft(siteId, mapId, UUID.randomUUID(), "Dra. Núñez", "Cardiología");
    maps.saveDraft(first);

    mvc.perform(search(siteId, "cardio")).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    commands.handle(new PublishMapVersionCommand(siteId, mapId, first.modelVersionId()));
    assertEquals(1,
        planning.findCurrentBySite(siteId).orElseThrow().destinations().size());
    mvc.perform(search(siteId, "cardio")).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Dra. Núñez"))
        .andExpect(jsonPath("$[0].modelVersionId").value(first.modelVersionId().toString()))
        .andExpect(jsonPath("$[0].type").value("DOCTOR"));
    mvc.perform(search(siteId, "nunez")).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    mvc.perform(search(siteId, "cirugia")).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(search(UUID.randomUUID(), "cardio")).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    IndoorMap next = draft(siteId, mapId, UUID.randomUUID(), "Farmacia", null);
    maps.saveDraft(next);
    mvc.perform(search(siteId, "farmacia")).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    commands.handle(new PublishMapVersionCommand(siteId, mapId, next.modelVersionId()));
    mvc.perform(search(siteId, "cardio")).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(search(siteId, "farmacia")).andExpect(status().isOk())
        .andExpect(jsonPath("$[0].modelVersionId").value(next.modelVersionId().toString()));
  }

  @Test
  void excludesEntryWhosePublishedNodeIsNoLongerADestination() throws Exception {
    UUID siteId = UUID.randomUUID();
    IndoorMap map = draft(siteId, UUID.randomUUID(), UUID.randomUUID(), "Farmacia", null);
    maps.saveDraft(map);
    commands.handle(new PublishMapVersionCommand(siteId, map.id(), map.modelVersionId()));
    UUID destinationNodeId = map.destinations().getFirst().nodeId();

    jdbc.update("UPDATE map_nodes SET node_type = 'INTERSECTION' "
        + "WHERE id = ? AND model_version_id = ?", destinationNodeId, map.modelVersionId());

    mvc.perform(search(siteId, "farmacia")).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void rejectsMissingTermAndMalformedSiteOrSearchTerm() throws Exception {
    UUID siteId = UUID.randomUUID();
    mvc.perform(get("/api/v1/sites/{siteId}/directory-entries", siteId))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/sites/{siteId}/directory-entries", "wrong")
        .param("searchTerm", "farmacia")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/sites/{siteId}/directory-entries", siteId)
        .param("searchTerm", " ")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void publishesSearchOperationInOpenApiWhenEnabled() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/sites/{siteId}/directory-entries'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/sites/{siteId}/directory-entries'].get.parameters[*].name")
            .value(hasItem("searchTerm")));
  }

  @Test
  void measuresSearchLatencyWithOneThousandPublishedEntries() throws Exception {
    UUID siteId = UUID.randomUUID();
    IndoorMap map = draft(siteId, UUID.randomUUID(), UUID.randomUUID(), "Farmacia", null);
    maps.saveDraft(map);
    commands.handle(new PublishMapVersionCommand(siteId, map.id(), map.modelVersionId()));
    UUID nodeId = map.destinations().getFirst().nodeId();
    jdbc.update("INSERT INTO map_destinations "
        + "(id, model_version_id, node_id, entry_type, name, specialty, search_text) "
        + "SELECT gen_random_uuid(), ?, ?, 'SERVICE', 'Servicio ' || n, NULL, "
        + "'servicio ' || n FROM generate_series(1, 1000) AS n",
        map.modelVersionId(), nodeId);
    for (int i = 0; i < 5; i++) mvc.perform(search(siteId, "servicio 999"));

    List<Long> millis = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      long started = System.nanoTime();
      mvc.perform(search(siteId, "servicio 999"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1));
      millis.add((System.nanoTime() - started) / 1_000_000);
    }
    Collections.sort(millis);
    System.out.println("Directory search with 1000 entries: p95="
        + millis.get(28) + "ms, max=" + millis.getLast() + "ms (30 MockMvc requests)");
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder search(
      UUID siteId, String term) {
    return get("/api/v1/sites/{siteId}/directory-entries", siteId).param("searchTerm", term);
  }

  private IndoorMap draft(UUID siteId, UUID mapId, UUID versionId,
                          String name, String specialty) {
    IndoorMap map = new IndoorMap(mapId, siteId, versionId);
    com.uitopic.rumbo.subscriptions.SubscriptionTestSeeds.enterpriseSite(jdbc, map.siteId());
    UUID floorId = UUID.randomUUID();
    UUID planVersionId = UUID.randomUUID();
    map.addPlan(new PlanCoordinateSpace(floorId, planVersionId, 100, 100));
    map.setScale(new MapScale(floorId, planVersionId, 0.25));
    UUID accessId = UUID.randomUUID();
    UUID destinationNodeId = UUID.randomUUID();
    map.addNode(accessId, floorId, planVersionId, 1, 1, NodeType.ACCESS);
    map.addNode(destinationNodeId, floorId, planVersionId, 2, 2, NodeType.DESTINATION);
    map.addConnection(UUID.randomUUID(), accessId, destinationNodeId,
        ConnectionType.CORRIDOR, 1, 2, true);
    map.addDestination(UUID.randomUUID(), destinationNodeId,
        specialty == null ? DirectoryEntryType.SERVICE : DirectoryEntryType.DOCTOR,
        name, specialty);
    return map;
  }
}
