package com.uitopic.rumbo.navigation.interfaces.rest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import com.uitopic.rumbo.iam.infrastructure.security.SiteOwnerGuard;
import com.uitopic.rumbo.planning.domain.model.commands.PublishMapVersionCommand;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionAccessibility;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapScale;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PlanCoordinateSpace;
import com.uitopic.rumbo.planning.domain.repositories.IndoorMapRepository;
import com.uitopic.rumbo.planning.domain.services.IndoorMapCommandService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Published route HTTP scenarios against versioned PostgreSQL graph data. */
@SpringBootTest(properties = "rumbo.navigation.routes.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "RUMBO_TEST_DB_URL", matches = ".+")
@WithMockUser(roles = "CLINIC_ADMIN")
class PublishedRoutesControllerIntegrationTest {
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
  @MockitoBean SiteOwnerGuard siteOwnerGuard;

  @BeforeEach void allowFixtureSites() {
    when(siteOwnerGuard.isOwner(any(), any())).thenReturn(true);
  }

  @Test
  void onlyPublishedVerticalRouteIsReturnedWithAppliedPenaltyAndAccessibility() throws Exception {
    Fixture fixture = draft(true, ConnectionAccessibility.ACCESSIBLE, true);
    mvc.perform(route(fixture.siteId(), fixture.startId(), fixture.goalId(), false))
        .andExpect(status().isNotFound());
    publish(fixture);

    mvc.perform(route(fixture.siteId(), fixture.startId(), fixture.goalId(), true))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.siteId").value(fixture.siteId().toString()))
        .andExpect(jsonPath("$.modelVersionId").value(fixture.map().modelVersionId().toString()))
        .andExpect(jsonPath("$.totalDistanceMeters").value(5.0))
        .andExpect(jsonPath("$.totalCost").value(10.0))
        .andExpect(jsonPath("$.nodes.length()").value(2))
        .andExpect(jsonPath("$.links[0].type").value("ELEVATOR"))
        .andExpect(jsonPath("$.links[0].verticalPenalty").value(3.0))
        .andExpect(jsonPath("$.links[0].accessibility").value("ACCESSIBLE"))
        .andExpect(jsonPath("$.verticalSections.length()").value(1))
        .andExpect(jsonPath("$.verticalSections[0].linkIndex").value(0))
        .andExpect(jsonPath("$.verticalSections[0].type").value("ELEVATOR"))
        .andExpect(jsonPath("$.verticalSections[0].fromFloorId")
            .value(fixture.map().nodes().getFirst().position().floorId().toString()))
        .andExpect(jsonPath("$.verticalSections[0].toFloorId")
            .value(fixture.map().nodes().getLast().position().floorId().toString()))
        .andExpect(jsonPath("$.verticalSections[0].cost").value(10.0));
    mvc.perform(route(fixture.siteId(), fixture.goalId(), fixture.startId(), false))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));
  }

  @Test
  void unknownAccessibilityIsNotOfferedAsAnAccessibleRoute() throws Exception {
    Fixture fixture = draft(false, ConnectionAccessibility.UNKNOWN, true);
    publish(fixture);
    mvc.perform(route(fixture.siteId(), fixture.startId(), fixture.goalId(), false))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verticalSections.length()").value(0));
    mvc.perform(route(fixture.siteId(), fixture.startId(), fixture.goalId(), true))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NO_COMPATIBLE_ROUTE"))
        .andExpect(jsonPath("$.message")
            .value("No route satisfies the requested accessibility preference"))
        .andExpect(jsonPath("$.nodes").doesNotExist())
        .andExpect(jsonPath("$.links").doesNotExist());
  }

  @Test
  void unreachableDestinationRemainsRouteNotFoundWithAccessibilityPreference() throws Exception {
    Fixture fixture = draft(false, ConnectionAccessibility.ACCESSIBLE, true);
    publish(fixture);
    mvc.perform(route(fixture.siteId(), fixture.goalId(), fixture.startId(), true))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));
  }

  @Test
  void distinguishesForeignNodesFromUnknownNodesAndInvalidParameters() throws Exception {
    Fixture own = draft(false, ConnectionAccessibility.ACCESSIBLE, true);
    Fixture foreign = draft(false, ConnectionAccessibility.ACCESSIBLE, true);
    publish(own);
    publish(foreign);

    mvc.perform(route(own.siteId(), own.startId(), foreign.goalId(), false))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ROUTE_REQUEST"));
    mvc.perform(route(own.siteId(), own.startId(), UUID.randomUUID(), false))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/sites/{siteId}/routes", own.siteId())
        .param("from", "invalid").param("to", own.goalId().toString()))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/sites/{siteId}/routes", own.siteId())
        .param("from", own.startId().toString()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void invalidPublishedCoordinatesReturnDefensiveError() throws Exception {
    Fixture fixture = draft(false, ConnectionAccessibility.ACCESSIBLE, true);
    publish(fixture);
    jdbc.update("UPDATE map_nodes SET x = 999 WHERE id = ? AND model_version_id = ?",
        fixture.goalId(), fixture.map().modelVersionId());
    mvc.perform(route(fixture.siteId(), fixture.startId(), fixture.goalId(), false))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("INVALID_PUBLISHED_MODEL"));
  }

  @Test
  void publishesRouteContractInOpenApiWhenEnabled() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/sites/{siteId}/routes'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/sites/{siteId}/routes'].get.parameters.length()")
            .value(4))
        .andExpect(jsonPath("$.components.schemas.PublishedRouteResponse.properties.verticalSections")
            .exists());
  }

  @Test
  void measuresWarmEndpointLatencyAgainstPublishedGrid() throws Exception {
    Fixture fixture = gridDraft(20, 15);
    publish(fixture);
    MockHttpServletRequestBuilder request = route(fixture.siteId(),
        fixture.startId(), fixture.goalId(), false);
    for (int i = 0; i < 10; i++) mvc.perform(request).andExpect(status().isOk());
    List<Long> millis = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      long started = System.nanoTime();
      mvc.perform(request).andExpect(status().isOk());
      millis.add((System.nanoTime() - started) / 1_000_000);
    }
    Collections.sort(millis);
    long p95 = millis.get(28);
    System.out.println("Published route, 300 nodes, 565 links, 30 warm MockMvc requests: "
        + "p95=" + p95 + "ms, max=" + millis.getLast() + "ms");
    assertTrue(p95 < 200, "TS-13 warm 300-node route p95 was " + p95 + "ms");
  }

  private MockHttpServletRequestBuilder route(UUID siteId, UUID from, UUID to,
                                              boolean accessibleOnly) {
    return get("/api/v1/sites/{siteId}/routes", siteId)
        .param("from", from.toString()).param("to", to.toString())
        .param("accessibleOnly", Boolean.toString(accessibleOnly));
  }

  private Fixture draft(boolean vertical, ConnectionAccessibility accessibility,
                        boolean oneWay) {
    UUID siteId = UUID.randomUUID();
    IndoorMap map = new IndoorMap(UUID.randomUUID(), siteId, UUID.randomUUID());
    UUID floor = UUID.randomUUID();
    UUID plan = UUID.randomUUID();
    map.addPlan(new PlanCoordinateSpace(floor, plan, 100, 100));
    map.setScale(new MapScale(floor, plan, 1));
    UUID goalFloor = vertical ? UUID.randomUUID() : floor;
    UUID goalPlan = vertical ? UUID.randomUUID() : plan;
    if (vertical) {
      map.addPlan(new PlanCoordinateSpace(goalFloor, goalPlan, 100, 100));
      map.setScale(new MapScale(goalFloor, goalPlan, 1));
    }
    UUID startId = UUID.randomUUID();
    UUID goalId = UUID.randomUUID();
    map.addNode(startId, floor, plan, 1, 2, NodeType.ACCESS);
    map.addNode(goalId, goalFloor, goalPlan, 2, 2, NodeType.DESTINATION);
    map.addConnection(UUID.randomUUID(), startId, goalId,
        vertical ? ConnectionType.ELEVATOR : ConnectionType.CORRIDOR,
        5, 7, vertical ? 3 : 0, accessibility, !oneWay);
    maps.saveDraft(map);
    return new Fixture(siteId, map, startId, goalId);
  }

  private Fixture gridDraft(int columns, int rows) {
    UUID siteId = UUID.randomUUID();
    IndoorMap map = new IndoorMap(UUID.randomUUID(), siteId, UUID.randomUUID());
    UUID floorId = UUID.randomUUID();
    UUID planVersionId = UUID.randomUUID();
    map.addPlan(new PlanCoordinateSpace(floorId, planVersionId, columns, rows));
    map.setScale(new MapScale(floorId, planVersionId, 1));
    UUID[][] nodeIds = new UUID[rows][columns];
    for (int row = 0; row < rows; row++) {
      for (int column = 0; column < columns; column++) {
        UUID nodeId = UUID.randomUUID();
        nodeIds[row][column] = nodeId;
        NodeType type = row == 0 && column == 0 ? NodeType.ACCESS
            : row == rows - 1 && column == columns - 1 ? NodeType.DESTINATION
            : NodeType.INTERSECTION;
        map.addNode(nodeId, floorId, planVersionId, column, row, type);
      }
    }
    for (int row = 0; row < rows; row++) {
      for (int column = 0; column < columns; column++) {
        if (column + 1 < columns) {
          map.addConnection(UUID.randomUUID(), nodeIds[row][column],
              nodeIds[row][column + 1], ConnectionType.CORRIDOR, 1, 1, 0,
              ConnectionAccessibility.ACCESSIBLE, true);
        }
        if (row + 1 < rows) {
          map.addConnection(UUID.randomUUID(), nodeIds[row][column],
              nodeIds[row + 1][column], ConnectionType.CORRIDOR, 1, 1, 0,
              ConnectionAccessibility.ACCESSIBLE, true);
        }
      }
    }
    maps.saveDraft(map);
    return new Fixture(siteId, map, nodeIds[0][0], nodeIds[rows - 1][columns - 1]);
  }

  private void publish(Fixture fixture) {
    commands.handle(new PublishMapVersionCommand(fixture.siteId(), fixture.map().id(),
        fixture.map().modelVersionId()));
  }

  private record Fixture(UUID siteId, IndoorMap map, UUID startId, UUID goalId) {}
}
