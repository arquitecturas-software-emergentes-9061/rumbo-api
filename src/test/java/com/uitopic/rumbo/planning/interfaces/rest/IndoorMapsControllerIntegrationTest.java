package com.uitopic.rumbo.planning.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.uitopic.rumbo.planning.domain.repositories.IndoorMapRepository;
import com.uitopic.rumbo.iam.infrastructure.security.SiteOwnerGuard;
import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import com.uitopic.rumbo.planning.domain.model.entities.MapConnection;
import com.uitopic.rumbo.planning.domain.model.entities.MapNode;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapScale;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PlanCoordinateSpace;
import com.uitopic.rumbo.planning.domain.model.valueobjects.SpatialCoordinate;
import java.util.UUID;
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

/** HTTP and PostgreSQL checks for atomic map publication. */
@SpringBootTest(properties = "rumbo.planning.publication.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "RUMBO_TEST_DB_URL", matches = ".+")
@WithMockUser(roles = "CLINIC_ADMIN")
class IndoorMapsControllerIntegrationTest {
  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", () -> System.getenv("RUMBO_TEST_DB_URL"));
    properties.add("spring.datasource.username", () -> System.getenv("RUMBO_TEST_DB_USER"));
    properties.add("spring.datasource.password", () -> System.getenv("RUMBO_TEST_DB_PASSWORD"));
  }

  @Autowired MockMvc mvc;
  @Autowired IndoorMapRepository store;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean SiteOwnerGuard siteOwnerGuard;

  @BeforeEach void allowFixtureSites() {
    when(siteOwnerGuard.isOwner(any(), any())).thenReturn(true);
  }

  @Test
  void publishesValidVersionArchivesPreviousAndRejectsRepeat() throws Exception {
    UUID siteId = UUID.randomUUID();
    UUID mapId = UUID.randomUUID();
    IndoorMap first = draft(siteId, mapId, UUID.randomUUID(), true);
    store.saveDraft(first);

    publish(first).andExpect(status().isOk())
        .andExpect(jsonPath("$.modelVersionId").value(first.modelVersionId().toString()))
        .andExpect(jsonPath("$.status").value("PUBLISHED"));
    assertEquals("PUBLISHED", storedStatus(first.modelVersionId()));
    publish(first).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MAP_VERSION_STATE_CONFLICT"));

    IndoorMap second = draft(siteId, mapId, UUID.randomUUID(), true);
    store.saveDraft(second);
    publish(second).andExpect(status().isOk());

    assertEquals("ARCHIVED", storedStatus(first.modelVersionId()));
    assertEquals("PUBLISHED", storedStatus(second.modelVersionId()));
  }

  @Test
  void invalidDraftReturnsLocatableIssuesAndKeepsPreviousPublished() throws Exception {
    UUID siteId = UUID.randomUUID();
    UUID mapId = UUID.randomUUID();
    IndoorMap previous = draft(siteId, mapId, UUID.randomUUID(), true);
    store.saveDraft(previous);
    publish(previous).andExpect(status().isOk());

    IndoorMap invalid = draft(siteId, mapId, UUID.randomUUID(), false);
    store.saveDraft(invalid);
    publish(invalid).andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("INVALID_MAP_GRAPH"))
        .andExpect(jsonPath("$.issues[0].code").value("DISCONNECTED_NODE"))
        .andExpect(jsonPath("$.issues[0].elementType").value("NODE"));

    assertEquals("DRAFT", storedStatus(invalid.modelVersionId()));
    assertEquals("PUBLISHED", storedStatus(previous.modelVersionId()));
  }

  @Test
  void storedDuplicateConnectionBlocksPublicationWithItsIdentifier() throws Exception {
    IndoorMap map = draft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), true);
    store.saveDraft(map);
    UUID duplicateId = UUID.randomUUID();
    jdbc.update("INSERT INTO map_connections (id, model_version_id, source_node_id, "
            + "target_node_id, connection_type, distance_meters, weight, bidirectional) "
            + "SELECT ?, model_version_id, source_node_id, target_node_id, connection_type, "
            + "distance_meters, weight, bidirectional FROM map_connections "
            + "WHERE id = ? AND model_version_id = ?",
        duplicateId, map.connections().getFirst().id(), map.modelVersionId());

    publish(map).andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("INVALID_MAP_GRAPH"))
        .andExpect(jsonPath("$.issues[0].code").value("DUPLICATE_CONNECTION"))
        .andExpect(jsonPath("$.issues[0].elementType").value("CONNECTION"))
        .andExpect(jsonPath("$.issues[0].elementId").exists());
    assertEquals("DRAFT", storedStatus(map.modelVersionId()));
  }

  @Test
  void storedOutOfBoundsNodeBlocksPublicationWithItsIdentifier() throws Exception {
    IndoorMap map = draft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), true);
    store.saveDraft(map);
    UUID nodeId = map.nodes().getFirst().id();
    jdbc.update("UPDATE map_nodes SET x = 1000 WHERE id = ? AND model_version_id = ?",
        nodeId, map.modelVersionId());

    publish(map).andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("INVALID_MAP_GRAPH"))
        .andExpect(jsonPath("$.issues[0].code").value("INVALID_NODE_DATA"))
        .andExpect(jsonPath("$.issues[0].elementType").value("NODE"))
        .andExpect(jsonPath("$.issues[0].elementId").value(nodeId.toString()));
    assertEquals("DRAFT", storedStatus(map.modelVersionId()));
  }

  @Test
  void rejectsUnknownSiteVersionAndUnsupportedStatus() throws Exception {
    IndoorMap map = draft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), true);
    store.saveDraft(map);

    mvc.perform(patch(url(UUID.randomUUID(), map.id(), map.modelVersionId()))
        .contentType("application/merge-patch+json")
        .content("{\"status\":\"PUBLISHED\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MAP_VERSION_NOT_FOUND"));
    mvc.perform(patch(url(map.siteId(), map.id(), UUID.randomUUID()))
        .contentType("application/merge-patch+json")
        .content("{\"status\":\"PUBLISHED\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(patch(url(map.siteId(), map.id(), map.modelVersionId()))
        .contentType("application/merge-patch+json")
        .content("{\"status\":\"ARCHIVED\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    mvc.perform(patch(url(map.siteId(), map.id(), map.modelVersionId()))
        .contentType("application/merge-patch+json")
        .content("{invalid json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    mvc.perform(patch("/api/v1/sites/invalid/maps/" + map.id() + "/versions/" + map.modelVersionId())
        .contentType("application/merge-patch+json")
        .content("{\"status\":\"PUBLISHED\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    assertEquals("DRAFT", storedStatus(map.modelVersionId()));
  }

  private org.springframework.test.web.servlet.ResultActions publish(IndoorMap map) throws Exception {
    return mvc.perform(patch(url(map.siteId(), map.id(), map.modelVersionId()))
        .contentType("application/merge-patch+json")
        .content("{\"status\":\"PUBLISHED\"}"));
  }

  private String url(UUID siteId, UUID mapId, UUID versionId) {
    return "/api/v1/sites/" + siteId + "/maps/" + mapId + "/versions/" + versionId;
  }

  private String storedStatus(UUID versionId) {
    return jdbc.queryForObject("SELECT status FROM map_versions WHERE id = ?", String.class, versionId);
  }

  private IndoorMap draft(UUID siteId, UUID mapId, UUID versionId, boolean connected) {
    IndoorMap map = new IndoorMap(mapId, siteId, versionId);
    com.uitopic.rumbo.subscriptions.SubscriptionTestSeeds.enterpriseSite(jdbc, map.siteId());
    UUID floorId = UUID.randomUUID();
    UUID planVersionId = UUID.randomUUID();
    PlanCoordinateSpace space = new PlanCoordinateSpace(floorId, planVersionId, 100, 100);
    map.addPlan(space);
    map.setScale(new MapScale(floorId, planVersionId, 0.25));
    MapNode access = new MapNode(UUID.randomUUID(), versionId,
        new SpatialCoordinate(floorId, planVersionId, 1, 1), NodeType.ACCESS, space);
    MapNode destination = new MapNode(UUID.randomUUID(), versionId,
        new SpatialCoordinate(floorId, planVersionId, 2, 1), NodeType.DESTINATION, space);
    map.addNode(access);
    map.addNode(destination);
    if (connected) {
      map.addConnection(new MapConnection(UUID.randomUUID(), versionId,
          access.id(), destination.id(), ConnectionType.CORRIDOR, 1, 1, true));
    }
    return map;
  }
}
