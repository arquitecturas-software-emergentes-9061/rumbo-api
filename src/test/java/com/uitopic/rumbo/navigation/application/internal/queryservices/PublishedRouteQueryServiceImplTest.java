package com.uitopic.rumbo.navigation.application.internal.queryservices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.uitopic.rumbo.navigation.domain.exceptions.NoCompatibleRouteException;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedSpatialGraph;
import com.uitopic.rumbo.navigation.domain.repositories.PublishedSpatialGraphRepository;
import com.uitopic.rumbo.navigation.domain.services.AStarRouteCalculationDomainService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Uses real A* calculations and a single-snapshot repository fake. */
class PublishedRouteQueryServiceImplTest {
  private final UUID siteId = UUID.randomUUID();
  private final UUID floorId = UUID.randomUUID();
  private final UUID planId = UUID.randomUUID();
  private final PublishedSpatialGraph.Node start = node(1);
  private final PublishedSpatialGraph.Node goal = node(2);
  private final AStarRouteCalculationDomainService calculator =
      spy(new AStarRouteCalculationDomainService());

  @Test
  void unknownAccessibilityReportsNoCompatibleRouteUsingTheSameLoadedGraph() {
    var graph = graph(true, PublishedSpatialGraph.Accessibility.UNKNOWN);
    var repository = new FakeGraphs(graph);
    var service = new PublishedRouteQueryServiceImpl(repository, calculator);

    assertThrows(NoCompatibleRouteException.class,
        () -> service.calculate(siteId, start.id(), goal.id(), true));

    assertEquals(1, repository.loads);
    verify(calculator).calculate(graph, start.id(), goal.id(), true);
    verify(calculator).calculate(graph, start.id(), goal.id(), false);
    verifyNoMoreInteractions(calculator);
  }

  @Test
  void unknownAccessibilityReturnsRouteWithoutRestriction() {
    var graph = graph(true, PublishedSpatialGraph.Accessibility.UNKNOWN);
    var repository = new FakeGraphs(graph);
    var service = new PublishedRouteQueryServiceImpl(repository, calculator);

    var route = service.calculate(siteId, start.id(), goal.id(), false).orElseThrow();

    assertEquals(List.of(start, goal), route.nodes());
    assertEquals(1, repository.loads);
    verify(calculator).calculate(graph, start.id(), goal.id(), false);
    verifyNoMoreInteractions(calculator);
  }

  @Test
  void disconnectedNodesReturnEmptyEvenWithAccessibilityPreference() {
    var graph = graph(false, PublishedSpatialGraph.Accessibility.ACCESSIBLE);
    var repository = new FakeGraphs(graph);
    var service = new PublishedRouteQueryServiceImpl(repository, calculator);

    assertTrue(service.calculate(siteId, start.id(), goal.id(), true).isEmpty());

    assertEquals(1, repository.loads);
    verify(calculator).calculate(graph, start.id(), goal.id(), true);
    verify(calculator).calculate(graph, start.id(), goal.id(), false);
    verifyNoMoreInteractions(calculator);
  }

  @Test
  void accessibleRouteIsReturnedWithoutRecalculation() {
    var graph = graph(true, PublishedSpatialGraph.Accessibility.ACCESSIBLE);
    var repository = new FakeGraphs(graph);
    var service = new PublishedRouteQueryServiceImpl(repository, calculator);

    var route = service.calculate(siteId, start.id(), goal.id(), true).orElseThrow();

    assertEquals(List.of(start, goal), route.nodes());
    assertEquals(PublishedSpatialGraph.Accessibility.ACCESSIBLE,
        route.legs().getFirst().accessibility());
    assertEquals(1, repository.loads);
    verify(calculator).calculate(graph, start.id(), goal.id(), true);
    verifyNoMoreInteractions(calculator);
  }

  private PublishedSpatialGraph.Node node(double x) {
    return new PublishedSpatialGraph.Node(UUID.randomUUID(), floorId, planId, x, 0,
        PublishedSpatialGraph.NodeRole.INTERSECTION);
  }

  private PublishedSpatialGraph graph(boolean connected,
                                       PublishedSpatialGraph.Accessibility accessibility) {
    List<PublishedSpatialGraph.Link> links = connected ? List.of(
        new PublishedSpatialGraph.Link(UUID.randomUUID(), start.id(), goal.id(),
            PublishedSpatialGraph.LinkKind.CORRIDOR, 1, 1, 0, accessibility, false))
        : List.of();
    return new PublishedSpatialGraph(siteId, UUID.randomUUID(), UUID.randomUUID(),
        List.of(new PublishedSpatialGraph.Plan(floorId, planId, 10, 10, 1)),
        List.of(start, goal), links);
  }

  private static final class FakeGraphs implements PublishedSpatialGraphRepository {
    private final PublishedSpatialGraph graph;
    private int loads;

    private FakeGraphs(PublishedSpatialGraph graph) {
      this.graph = graph;
    }

    @Override
    public Optional<PublishedSpatialGraph> findCurrentBySite(UUID siteId) {
      loads++;
      return graph.siteId().equals(siteId) ? Optional.of(graph) : Optional.empty();
    }

    @Override
    public boolean existsCurrentNodeOutsideSite(UUID nodeId, UUID siteId) {
      return false;
    }
  }
}
