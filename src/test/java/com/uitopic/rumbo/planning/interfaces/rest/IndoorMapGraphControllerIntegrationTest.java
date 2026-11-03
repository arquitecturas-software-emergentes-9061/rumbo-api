package com.uitopic.rumbo.planning.interfaces.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.planning.domain.repositories.IndoorMapRepository;
import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import com.uitopic.rumbo.planning.domain.model.entities.MapConnection;
import com.uitopic.rumbo.planning.domain.model.entities.MapNode;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapScale;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PlanCoordinateSpace;
import com.uitopic.rumbo.planning.domain.model.valueobjects.SpatialCoordinate;
import com.uitopic.rumbo.planning.interfaces.rest.resources.GraphUpdateResource;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP and PostgreSQL checks for reading and atomically replacing draft graphs. */
@SpringBootTest(properties = {"rumbo.planning.editing.enabled=true",
    "rumbo.planning.publication.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "RUMBO_TEST_DB_URL", matches = ".+")
class IndoorMapGraphControllerIntegrationTest {
  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", () -> System.getenv("RUMBO_TEST_DB_URL"));
    properties.add("spring.datasource.username", () -> System.getenv("RUMBO_TEST_DB_USER"));
    properties.add("spring.datasource.password", () -> System.getenv("RUMBO_TEST_DB_PASSWORD"));
  }

  @Autowired MockMvc mvc;
  @Autowired IndoorMapRepository store;
  @Autowired ObjectMapper json;

  @Test
  void readsAndReplacesGraphWithoutLosingPlanOrScale() throws Exception {
    IndoorMap map = draft();
    store.saveDraft(map);
    UUID accessId = map.nodes().get(0).id();
    UUID destinationId = map.nodes().get(1).id();
    UUID floorId = map.nodes().get(0).position().floorId();
    UUID planVersionId = map.nodes().get(0).position().planVersionId();
    UUID connectionId = UUID.randomUUID();

    GraphUpdateResource update = new GraphUpdateResource(List.of(
        new GraphUpdateResource.NodeInputResource(accessId, floorId, planVersionId,
            5.0, 6.0, NodeType.ACCESS),
        new GraphUpdateResource.NodeInputResource(destinationId, floorId, planVersionId,
            20.0, 6.0, NodeType.DESTINATION)),
        List.of(new GraphUpdateResource.ConnectionInputResource(connectionId, accessId,
            destinationId, ConnectionType.CORRIDOR, 3.75, 5.0, true)));

    mvc.perform(put(url(map)).contentType("application/json")
        .content(json.writeValueAsString(update)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.plans[0].metersPerUnit").value(0.25))
        .andExpect(jsonPath("$.connections[0].id").value(connectionId.toString()));

    mvc.perform(get(url(map))).andExpect(status().isOk())
        .andExpect(jsonPath("$.nodes.length()").value(2))
        .andExpect(jsonPath("$.connections[0].weight").value(5.0));
    IndoorMap reloaded = store.findDraft(map.id(), map.modelVersionId()).orElseThrow();
    assertEquals(map.plans(), reloaded.plans());
    assertEquals(map.scales(), reloaded.scales());
    assertEquals(connectionId, reloaded.connections().getFirst().id());
    assertEquals(5.0, reloaded.nodes().stream().filter(node -> node.id().equals(accessId))
        .findFirst().orElseThrow().position().x());
    assertEquals(20.0, reloaded.nodes().stream().filter(node -> node.id().equals(destinationId))
        .findFirst().orElseThrow().position().x());
  }

  @Test
  void invalidConnectionReturnsItsIdAndPreservesStoredGraph() throws Exception {
    IndoorMap map = draft();
    store.saveDraft(map);
    UUID connectionId = UUID.randomUUID();
    UUID unknownNodeId = UUID.randomUUID();
    GraphUpdateResource update = new GraphUpdateResource(nodeInputs(map), List.of(
        new GraphUpdateResource.ConnectionInputResource(connectionId,
            map.nodes().getFirst().id(), unknownNodeId, ConnectionType.CORRIDOR,
            1.0, 2.0, true)));

    mvc.perform(put(url(map)).contentType("application/json")
        .content(json.writeValueAsString(update)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("INVALID_MAP_GRAPH"))
        .andExpect(jsonPath("$.issues[0].code").value("MISSING_TARGET_NODE"))
        .andExpect(jsonPath("$.issues[0].elementId").value(connectionId.toString()));
    assertEquals(map.connections().getFirst().id(),
        store.findDraft(map.id(), map.modelVersionId()).orElseThrow().connections().getFirst().id());
  }

  @Test
  void outOfBoundsNodeReturnsItsIdAndPreservesStoredGraph() throws Exception {
    IndoorMap map = draft();
    store.saveDraft(map);
    GraphUpdateResource.NodeInputResource original = nodeInputs(map).getFirst();
    GraphUpdateResource update = new GraphUpdateResource(List.of(
        new GraphUpdateResource.NodeInputResource(original.id(), original.floorId(),
            original.planVersionId(), 1000.0, original.y(), original.type()),
        nodeInputs(map).get(1)), List.of());

    mvc.perform(put(url(map)).contentType("application/json")
        .content(json.writeValueAsString(update)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.issues[0].code").value("INVALID_NODE_DATA"))
        .andExpect(jsonPath("$.issues[0].elementId").value(original.id().toString()));
    assertEquals(1.0, store.findDraft(map.id(), map.modelVersionId()).orElseThrow().nodes()
        .stream().filter(node -> node.id().equals(original.id()))
        .findFirst().orElseThrow().position().x());
  }

  @Test
  void rejectsWrongSiteMalformedBodyAndEditsAfterPublication() throws Exception {
    IndoorMap map = draft();
    store.saveDraft(map);
    GraphUpdateResource update = new GraphUpdateResource(nodeInputs(map), connectionInputs(map));
    mvc.perform(get(url(UUID.randomUUID(), map.id(), map.modelVersionId())))
        .andExpect(status().isNotFound());
    mvc.perform(put(url(UUID.randomUUID(), map.id(), map.modelVersionId()))
        .contentType("application/json").content(json.writeValueAsString(update)))
        .andExpect(status().isNotFound());
    mvc.perform(put(url(map)).contentType("application/json")
        .content("{\"nodes\":[],\"connections\":[{}]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    mvc.perform(patch(url(map).replace("/graph", ""))
        .contentType("application/merge-patch+json")
        .content("{\"status\":\"PUBLISHED\"}"))
        .andExpect(status().isOk());
    mvc.perform(put(url(map)).contentType("application/json")
        .content(json.writeValueAsString(update)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MAP_VERSION_STATE_CONFLICT"));
    mvc.perform(get(url(map))).andExpect(status().isNotFound());
  }

  private List<GraphUpdateResource.NodeInputResource> nodeInputs(IndoorMap map) {
    return map.nodes().stream().map(node -> new GraphUpdateResource.NodeInputResource(
        node.id(), node.position().floorId(), node.position().planVersionId(),
        node.position().x(), node.position().y(), node.type())).toList();
  }

  private List<GraphUpdateResource.ConnectionInputResource> connectionInputs(IndoorMap map) {
    return map.connections().stream().map(connection ->
        new GraphUpdateResource.ConnectionInputResource(connection.id(), connection.sourceNodeId(),
            connection.targetNodeId(), connection.type(), connection.distanceMeters(),
            connection.weight(), connection.bidirectional())).toList();
  }

  private String url(IndoorMap map) {
    return url(map.siteId(), map.id(), map.modelVersionId());
  }

  private String url(UUID siteId, UUID mapId, UUID versionId) {
    return "/api/v1/sites/" + siteId + "/maps/" + mapId + "/versions/" + versionId + "/graph";
  }

  private IndoorMap draft() {
    UUID versionId = UUID.randomUUID();
    IndoorMap map = new IndoorMap(UUID.randomUUID(), UUID.randomUUID(), versionId);
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
    map.addConnection(new MapConnection(UUID.randomUUID(), versionId,
        access.id(), destination.id(), ConnectionType.CORRIDOR, 1, 1, true));
    return map;
  }
}
