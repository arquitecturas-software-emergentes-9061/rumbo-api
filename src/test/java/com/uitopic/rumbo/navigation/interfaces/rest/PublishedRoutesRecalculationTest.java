package com.uitopic.rumbo.navigation.interfaces.rest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uitopic.rumbo.navigation.application.internal.queryservices.PublishedRouteQueryServiceImpl;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedSpatialGraph;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedSpatialGraph.Accessibility;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedSpatialGraph.LinkKind;
import com.uitopic.rumbo.navigation.domain.repositories.PublishedSpatialGraphRepository;
import com.uitopic.rumbo.navigation.domain.repositories.VisitorRoutePreferencesRepository;
import com.uitopic.rumbo.navigation.domain.services.AStarRouteCalculationDomainService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** TS-22 HTTP contract with the real application service and A*; persistence and security are tested separately. */
class PublishedRoutesRecalculationTest {
  private final UUID siteId = UUID.randomUUID();
  private final UUID mapId = UUID.randomUUID();
  private final UUID lowerFloor = UUID.randomUUID();
  private final UUID upperFloor = UUID.randomUUID();
  private final UUID lowerPlan = UUID.randomUUID();
  private final UUID upperPlan = UUID.randomUUID();
  private final PublishedSpatialGraph.Node original = node(lowerFloor, lowerPlan, 0);
  private final PublishedSpatialGraph.Node updated = node(lowerFloor, lowerPlan, 1);
  private final PublishedSpatialGraph.Node landing = node(upperFloor, upperPlan, 1);
  private final PublishedSpatialGraph.Node goal = node(upperFloor, upperPlan, 4);
  private final PublishedSpatialGraph.Node isolated = node(lowerFloor, lowerPlan, 7);
  private final PublishedSpatialGraph.Link approach = link(original, updated, LinkKind.CORRIDOR, 2, 2, 0);
  private final PublishedSpatialGraph.Link stairs = link(updated, goal, LinkKind.STAIRS, 3, 2, 1);
  private final PublishedSpatialGraph.Link ramp = link(updated, goal, LinkKind.RAMP, 6, 3, 2);
  private final PublishedSpatialGraph.Link elevator = link(updated, landing, LinkKind.ELEVATOR, 5, 4, 2);
  private final PublishedSpatialGraph.Link exit = link(landing, goal, LinkKind.CORRIDOR, 3, 2, 0);
  private final PublishedSpatialGraph graph = graph(UUID.randomUUID(),
      List.of(approach, stairs, ramp, elevator, exit));
  private final PublishedSpatialGraphRepository graphs = mock(PublishedSpatialGraphRepository.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    var preferences = new DefaultListableBeanFactory().getBeanProvider(VisitorRoutePreferencesRepository.class);
    var service = new PublishedRouteQueryServiceImpl(graphs, new AStarRouteCalculationDomainService(), preferences);
    mvc = MockMvcBuilders.standaloneSetup(new PublishedRoutesController(service))
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .setControllerAdvice(new PublishedRoutesExceptionHandler()).build();
    when(graphs.findCurrentBySite(siteId)).thenReturn(Optional.of(graph));
  }

  @Test
  void updatedOriginKeepsTheDestinationAndReturnsANewOrderedAccessibleRoute() throws Exception {
    request(original.id(), true, true, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nodes[0].nodeId").value(original.id().toString()))
        .andExpect(jsonPath("$.nodes[3].nodeId").value(goal.id().toString()))
        .andExpect(jsonPath("$.totalCost").value(10.0));

    request(updated.id(), true, true, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.siteId").value(siteId.toString()))
        .andExpect(jsonPath("$.modelVersionId").value(graph.modelVersionId().toString()))
        .andExpect(jsonPath("$.nodes.length()").value(3))
        .andExpect(jsonPath("$.nodes[0].nodeId").value(updated.id().toString()))
        .andExpect(jsonPath("$.nodes[1].nodeId").value(landing.id().toString()))
        .andExpect(jsonPath("$.nodes[2].nodeId").value(goal.id().toString()))
        .andExpect(jsonPath("$.links.length()").value(2))
        .andExpect(jsonPath("$.links[0].linkId").value(elevator.id().toString()))
        .andExpect(jsonPath("$.links[0].fromNodeId").value(updated.id().toString()))
        .andExpect(jsonPath("$.links[0].toNodeId").value(landing.id().toString()))
        .andExpect(jsonPath("$.links[0].accessibility").value("ACCESSIBLE"))
        .andExpect(jsonPath("$.links[1].linkId").value(exit.id().toString()))
        .andExpect(jsonPath("$.links[1].toNodeId").value(goal.id().toString()))
        .andExpect(jsonPath("$.totalDistanceMeters").value(8.0))
        .andExpect(jsonPath("$.totalCost").value(8.0))
        .andExpect(jsonPath("$.verticalSections.length()").value(1))
        .andExpect(jsonPath("$.verticalSections[0].linkIndex").value(0))
        .andExpect(jsonPath("$.verticalSections[0].linkId").value(elevator.id().toString()))
        .andExpect(jsonPath("$.verticalSections[0].fromFloorId").value(lowerFloor.toString()))
        .andExpect(jsonPath("$.verticalSections[0].toFloorId").value(upperFloor.toString()))
        .andExpect(jsonPath("$.verticalSections[0].cost").value(6.0));
  }

  @Test
  void originOnTheNextFloorDropsTheAlreadyTraversedVerticalSection() throws Exception {
    request(updated.id(), true, true, true).andExpect(status().isOk())
        .andExpect(jsonPath("$.verticalSections.length()").value(1));

    request(landing.id(), true, true, true).andExpect(status().isOk())
        .andExpect(jsonPath("$.nodes.length()").value(2))
        .andExpect(jsonPath("$.nodes[0].floorId").value(upperFloor.toString()))
        .andExpect(jsonPath("$.nodes[1].nodeId").value(goal.id().toString()))
        .andExpect(jsonPath("$.links[0].linkId").value(exit.id().toString()))
        .andExpect(jsonPath("$.totalDistanceMeters").value(3.0))
        .andExpect(jsonPath("$.totalCost").value(2.0))
        .andExpect(jsonPath("$.verticalSections.length()").value(0));
  }

  @ParameterizedTest
  @CsvSource({"true,false,false,RAMP", "false,true,false,RAMP", "false,false,true,ELEVATOR"})
  void eachMobilityPreferenceStillAppliesAfterTheOriginChanges(boolean accessibleOnly,
      boolean avoidStairs, boolean prioritizeElevators, String expectedType) throws Exception {
    request(original.id(), accessibleOnly, avoidStairs, prioritizeElevators).andExpect(status().isOk());

    request(updated.id(), accessibleOnly, avoidStairs, prioritizeElevators).andExpect(status().isOk())
        .andExpect(jsonPath("$.links[0].type").value(expectedType))
        .andExpect(jsonPath("$.nodes[0].nodeId").value(updated.id().toString()))
        .andExpect(jsonPath("$.nodes[-1].nodeId").value(goal.id().toString()));
  }

  @Test
  void consecutiveRequestsUseTheCurrentPublicationAndNeverReuseAnOldPath() throws Exception {
    var replacement = link(updated, landing, LinkKind.ELEVATOR, 7, 9, 1);
    var latest = graph(UUID.randomUUID(), List.of(approach, replacement, exit));
    when(graphs.findCurrentBySite(siteId)).thenReturn(Optional.of(graph)).thenReturn(Optional.of(latest));

    request(original.id(), true, true, true).andExpect(status().isOk())
        .andExpect(jsonPath("$.modelVersionId").value(graph.modelVersionId().toString()));
    request(updated.id(), true, true, true).andExpect(status().isOk())
        .andExpect(jsonPath("$.modelVersionId").value(latest.modelVersionId().toString()))
        .andExpect(jsonPath("$.links[0].linkId").value(replacement.id().toString()))
        .andExpect(jsonPath("$.totalDistanceMeters").value(10.0))
        .andExpect(jsonPath("$.totalCost").value(12.0))
        .andExpect(jsonPath("$.nodes[-1].nodeId").value(goal.id().toString()));
    request(landing.id(), true, true, true).andExpect(status().isOk())
        .andExpect(jsonPath("$.modelVersionId").value(latest.modelVersionId().toString()))
        .andExpect(jsonPath("$.links.length()").value(1))
        .andExpect(jsonPath("$.totalCost").value(2.0))
        .andExpect(jsonPath("$.nodes[-1].nodeId").value(goal.id().toString()));

    verify(graphs, times(3)).findCurrentBySite(siteId);
  }

  @Test
  void unreachableUpdatedOriginDoesNotReturnThePreviousRoute() throws Exception {
    request(original.id(), true, true, true).andExpect(status().isOk());

    request(isolated.id(), true, true, true).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"))
        .andExpect(jsonPath("$.nodes").doesNotExist());
    // All connections are directed: the upper-floor landing cannot go back to the lower origin.
    mvc.perform(get("/api/v1/sites/{siteId}/routes", siteId)
        .param("from", landing.id().toString()).param("to", original.id().toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));
  }

  @Test
  void incompatibleUpdatedRouteKeepsTheExistingErrorSemantics() throws Exception {
    when(graphs.findCurrentBySite(siteId)).thenReturn(Optional.of(graph(UUID.randomUUID(),
        List.of(approach, stairs, exit))));
    request(original.id(), false, false, false).andExpect(status().isOk());

    request(updated.id(), true, true, false).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NO_COMPATIBLE_ROUTE"))
        .andExpect(jsonPath("$.nodes").doesNotExist())
        .andExpect(jsonPath("$.links").doesNotExist());
  }

  @Test
  void unknownAndForeignOriginsAreRejectedWithoutReturningAPartialRoute() throws Exception {
    UUID unknown = UUID.randomUUID();
    UUID foreign = UUID.randomUUID();
    when(graphs.existsCurrentNodeOutsideSite(foreign, siteId)).thenReturn(true);

    request(unknown, false, false, false).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));
    request(foreign, false, false, false).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ROUTE_REQUEST"))
        .andExpect(jsonPath("$.nodes").doesNotExist());
    mvc.perform(get("/api/v1/sites/{siteId}/routes", siteId)
        .param("from", "invalid").param("to", goal.id().toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ROUTE_REQUEST"));
  }

  @Test
  void invalidCurrentPublicationIsNotReplacedByThePreviousRoute() throws Exception {
    var invalid = graph(UUID.randomUUID(), List.of(link(updated, goal, LinkKind.CORRIDOR, 1, 1, 0)));
    when(graphs.findCurrentBySite(siteId)).thenReturn(Optional.of(graph)).thenReturn(Optional.of(invalid));
    request(original.id(), true, true, true).andExpect(status().isOk());

    request(updated.id(), true, true, true).andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("INVALID_PUBLISHED_MODEL"))
        .andExpect(jsonPath("$.nodes").doesNotExist());
  }

  private ResultActions request(UUID from, boolean accessibleOnly, boolean avoidStairs,
                                boolean prioritizeElevators) throws Exception {
    return mvc.perform(get("/api/v1/sites/{siteId}/routes", siteId)
        .param("from", from.toString()).param("to", goal.id().toString())
        .param("accessibleOnly", Boolean.toString(accessibleOnly))
        .param("avoidStairs", Boolean.toString(avoidStairs))
        .param("prioritizeElevators", Boolean.toString(prioritizeElevators)));
  }

  private PublishedSpatialGraph graph(UUID version, List<PublishedSpatialGraph.Link> links) {
    return new PublishedSpatialGraph(siteId, mapId, version,
        List.of(new PublishedSpatialGraph.Plan(lowerFloor, lowerPlan, 10, 10, 1),
            new PublishedSpatialGraph.Plan(upperFloor, upperPlan, 10, 10, 1)),
        List.of(original, updated, landing, goal, isolated), links);
  }

  private PublishedSpatialGraph.Node node(UUID floor, UUID plan, double x) {
    return new PublishedSpatialGraph.Node(UUID.randomUUID(), floor, plan, x, 0,
        PublishedSpatialGraph.NodeRole.INTERSECTION);
  }

  private PublishedSpatialGraph.Link link(PublishedSpatialGraph.Node from, PublishedSpatialGraph.Node to,
      LinkKind kind, double distance, double weight, double penalty) {
    return new PublishedSpatialGraph.Link(UUID.randomUUID(), from.id(), to.id(), kind, distance, weight, penalty,
        kind == LinkKind.STAIRS ? Accessibility.INACCESSIBLE : Accessibility.ACCESSIBLE, false);
  }
}
