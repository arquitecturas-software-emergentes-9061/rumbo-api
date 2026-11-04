package com.uitopic.rumbo.planning.interfaces.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionAccessibility;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.DirectoryEntryType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapScale;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PlanCoordinateSpace;
import com.uitopic.rumbo.planning.domain.repositories.IndoorMapRepository;
import com.uitopic.rumbo.planning.domain.repositories.PublishedMapRepository;
import com.uitopic.rumbo.planning.interfaces.rest.resources.GraphUpdateResource;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Exercises creation, editing, and publication of successive PostgreSQL map versions. */
@SpringBootTest(properties = {"rumbo.planning.publication.enabled=true",
    "rumbo.planning.editing.enabled=true", "rumbo.navigation.routes.enabled=true",
    "rumbo.navigation.directory.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "RUMBO_TEST_DB_URL", matches = ".+")
class MapVersionLifecycleIntegrationTest {
  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", () -> System.getenv("RUMBO_TEST_DB_URL"));
    properties.add("spring.datasource.username", () -> System.getenv("RUMBO_TEST_DB_USER"));
    properties.add("spring.datasource.password", () -> System.getenv("RUMBO_TEST_DB_PASSWORD"));
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired IndoorMapRepository maps;
  @Autowired PublishedMapRepository published;
  @Autowired JdbcTemplate jdbc;

  @Test
  void createsEditsAndPublishesSuccessorWithoutChangingCurrentVersionEarly() throws Exception {
    IndoorMap first = sourceDraft();
    maps.saveDraft(first);
    publish(first.siteId(), first.id(), first.modelVersionId());
    UUID accessId = first.nodes().getFirst().id();
    UUID destinationId = first.nodes().getLast().id();

    var created = mvc.perform(post(versionsUrl(first.siteId(), first.id()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody(first.modelVersionId())))
        .andExpect(status().isCreated())
        .andExpect(header().string(HttpHeaders.ETAG, "\"rev-0\""))
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.plans[0].metersPerUnit").value(0.5))
        .andExpect(jsonPath("$.nodes.length()").value(2))
        .andExpect(jsonPath("$.connections[0].accessibility").value("ACCESSIBLE"))
        .andExpect(jsonPath("$.destinations[0].name").value("Cardio"))
        .andReturn().getResponse();
    JsonNode body = json.readTree(created.getContentAsString());
    UUID nextId = UUID.fromString(body.path("modelVersionId").asText());
    assertNotEquals(first.modelVersionId(), nextId);
    String graphUrl = versionsUrl(first.siteId(), first.id()) + "/" + nextId + "/graph";
    assertEquals(graphUrl, created.getHeader(HttpHeaders.LOCATION));
    assertEquals("PUBLISHED", storedStatus(first.modelVersionId()));
    assertEquals(first.modelVersionId(), published.findBySite(first.siteId()).orElseThrow().modelVersionId());
    assertEquals(nextId, maps.findDraft(first.id(), nextId).orElseThrow()
        .destinations().getFirst().modelVersionId());

    mvc.perform(get(graphUrl)).andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ETAG, "\"rev-0\""));
    var access = first.nodes().getFirst();
    var destination = first.nodes().getLast();
    var link = first.connections().getFirst();
    var entry = first.destinations().getFirst();
    GraphUpdateResource changed = new GraphUpdateResource(
        List.of(new GraphUpdateResource.NodeInputResource(access.id(),
                access.position().floorId(), access.position().planVersionId(),
                access.position().x(), access.position().y(), access.type()),
            new GraphUpdateResource.NodeInputResource(destination.id(),
                destination.position().floorId(), destination.position().planVersionId(),
                destination.position().x(), destination.position().y(), destination.type())),
        List.of(new GraphUpdateResource.ConnectionInputResource(link.id(),
            link.sourceNodeId(), link.targetNodeId(), link.type(), link.distanceMeters(),
            9.0, 0.0, ConnectionAccessibility.UNKNOWN, link.bidirectional())),
        List.of(new GraphUpdateResource.DestinationInputResource(entry.id(),
            entry.nodeId(), entry.type(), "Farmacia Nueva", null)));
    mvc.perform(put(graphUrl).header(HttpHeaders.IF_MATCH, "\"rev-0\"")
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(changed)))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ETAG, "\"rev-1\""));
    assertEquals(first.modelVersionId(), published.findBySite(first.siteId()).orElseThrow().modelVersionId());
    mvc.perform(get(routesUrl(first.siteId())).param("from", accessId.toString())
            .param("to", destinationId.toString()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalCost").value(2.0));

    publish(first.siteId(), first.id(), nextId);
    assertEquals("ARCHIVED", storedStatus(first.modelVersionId()));
    assertEquals("PUBLISHED", storedStatus(nextId));
    assertEquals(nextId, published.findBySite(first.siteId()).orElseThrow().modelVersionId());
    mvc.perform(get(routesUrl(first.siteId())).param("from", accessId.toString())
            .param("to", destinationId.toString()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.modelVersionId").value(nextId.toString()))
        .andExpect(jsonPath("$.totalCost").value(9.0));
    mvc.perform(get(routesUrl(first.siteId())).param("from", accessId.toString())
            .param("to", destinationId.toString()).param("accessibleOnly", "true"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/sites/{siteId}/directory-entries", first.siteId())
            .param("q", "farmacia"))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Farmacia Nueva"));
    mvc.perform(get(graphUrl)).andExpect(status().isNotFound());
    mvc.perform(post(versionsUrl(first.siteId(), first.id()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody(first.modelVersionId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SOURCE_VERSION_NOT_CURRENT"));
    mvc.perform(post(versionsUrl(first.siteId(), first.id()))
            .contentType(MediaType.APPLICATION_JSON).content(createBody(nextId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("DRAFT"));
  }

  @Test
  void rejectsDuplicateDraftWrongSiteAndOutdatedSource() throws Exception {
    IndoorMap first = sourceDraft();
    maps.saveDraft(first);
    publish(first.siteId(), first.id(), first.modelVersionId());
    String url = versionsUrl(first.siteId(), first.id());

    mvc.perform(post(versionsUrl(UUID.randomUUID(), first.id()))
            .contentType(MediaType.APPLICATION_JSON).content(createBody(first.modelVersionId())))
        .andExpect(status().isNotFound());
    mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
            .content(createBody(UUID.randomUUID())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SOURCE_VERSION_NOT_CURRENT"));
    mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
            .content(createBody(first.modelVersionId())))
        .andExpect(status().isCreated());
    mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
            .content(createBody(first.modelVersionId())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DRAFT_ALREADY_EXISTS"));
    assertEquals("PUBLISHED", storedStatus(first.modelVersionId()));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM map_versions "
        + "WHERE map_id = ? AND status = 'DRAFT'", Integer.class, first.id()));
  }

  @Test
  void invalidPublishedSourceDoesNotCreatePartialDraft() throws Exception {
    IndoorMap first = sourceDraft();
    maps.saveDraft(first);
    publish(first.siteId(), first.id(), first.modelVersionId());
    jdbc.update("UPDATE plan_coordinate_spaces SET meters_per_unit = NULL "
            + "WHERE model_version_id = ?", first.modelVersionId());

    mvc.perform(post(versionsUrl(first.siteId(), first.id()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody(first.modelVersionId())))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("INVALID_PUBLISHED_MODEL"));
    assertEquals("PUBLISHED", storedStatus(first.modelVersionId()));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM map_versions "
        + "WHERE map_id = ? AND status = 'DRAFT'", Integer.class, first.id()));
  }

  private IndoorMap sourceDraft() {
    UUID siteId = UUID.randomUUID();
    UUID mapId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    UUID floorId = UUID.randomUUID();
    UUID planId = UUID.randomUUID();
    IndoorMap map = new IndoorMap(mapId, siteId, versionId);
    map.addPlan(new PlanCoordinateSpace(floorId, planId, 20, 20));
    map.setScale(new MapScale(floorId, planId, 0.5));
    UUID accessId = UUID.randomUUID();
    UUID destinationId = UUID.randomUUID();
    map.addNode(accessId, floorId, planId, 1, 1, NodeType.ACCESS);
    map.addNode(destinationId, floorId, planId, 3, 1, NodeType.DESTINATION);
    map.addConnection(UUID.randomUUID(), accessId, destinationId, ConnectionType.CORRIDOR,
        1, 2, 0, ConnectionAccessibility.ACCESSIBLE, true);
    map.addDestination(UUID.randomUUID(), destinationId, DirectoryEntryType.SERVICE,
        "Cardio", null);
    return map;
  }

  private void publish(UUID siteId, UUID mapId, UUID versionId) throws Exception {
    mvc.perform(patch(versionsUrl(siteId, mapId) + "/" + versionId)
            .contentType("application/merge-patch+json")
            .content("{\"status\":\"PUBLISHED\"}"))
        .andExpect(status().isOk());
  }

  private String versionsUrl(UUID siteId, UUID mapId) {
    return "/api/v1/sites/" + siteId + "/maps/" + mapId + "/versions";
  }

  private String routesUrl(UUID siteId) {
    return "/api/v1/sites/" + siteId + "/routes";
  }

  private String createBody(UUID sourceVersionId) {
    return "{\"sourceVersionId\":\"" + sourceVersionId + "\"}";
  }

  private String storedStatus(UUID versionId) {
    return jdbc.queryForObject("SELECT status FROM map_versions WHERE id = ?",
        String.class, versionId);
  }
}
