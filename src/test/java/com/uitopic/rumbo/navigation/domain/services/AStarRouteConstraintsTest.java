package com.uitopic.rumbo.navigation.domain.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.uitopic.rumbo.navigation.domain.model.valueobjects.CalculatedRoute;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedSpatialGraph;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedSpatialGraph.Accessibility;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedSpatialGraph.LinkKind;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.RouteConstraints;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * TS-29 (WI-TS29-03 and WI-TS29-05): routes adapted to the visitor's mobility restrictions. Two floors joined by
 * stairs, a ramp and an elevator; the published costs make the stairs the cheapest option without restrictions.
 */
class AStarRouteConstraintsTest {
  private final AStarRouteCalculationDomainService calculator = new AStarRouteCalculationDomainService();
  private final UUID lowerFloor = UUID.randomUUID();
  private final UUID upperFloor = UUID.randomUUID();
  private final UUID lowerPlan = UUID.randomUUID();
  private final UUID upperPlan = UUID.randomUUID();
  private final PublishedSpatialGraph.Node lobby = node(lowerFloor, lowerPlan, 0);
  private final PublishedSpatialGraph.Node cardiology = node(upperFloor, upperPlan, 4);

  private final PublishedSpatialGraph.Link stairs =
      link(LinkKind.STAIRS, 2, 2, Accessibility.INACCESSIBLE);
  private final PublishedSpatialGraph.Link ramp =
      link(LinkKind.RAMP, 3, 2, Accessibility.ACCESSIBLE);
  private final PublishedSpatialGraph.Link elevator =
      link(LinkKind.ELEVATOR, 4, 3, Accessibility.ACCESSIBLE);

  @Test
  void withoutRestrictionsTheCheapestPublishedConnectionWins() {
    CalculatedRoute route = route(List.of(stairs, ramp, elevator), RouteConstraints.NONE);

    assertEquals(List.of(stairs.id()), linkIds(route));
    assertEquals(4, route.totalCost(), 1e-9);
  }

  @Test
  void avoidStairsTakesTheNextCheapestConnection() {
    CalculatedRoute route = route(List.of(stairs, ramp, elevator), new RouteConstraints(true, false, false));

    assertEquals(List.of(ramp.id()), linkIds(route));
  }

  @Test
  void prioritizeElevatorsPrefersTheElevatorAndReportsItsPublishedCost() {
    CalculatedRoute route = route(List.of(stairs, ramp, elevator), new RouteConstraints(false, false, true));

    assertEquals(List.of(elevator.id()), linkIds(route));
    assertEquals(7, route.totalCost(), 1e-9);
  }

  @Test
  void prioritizeElevatorsStillUsesStairsWhenTheyAreTheOnlyWay() {
    CalculatedRoute route = route(List.of(stairs), new RouteConstraints(false, false, true));

    assertEquals(List.of(stairs.id()), linkIds(route));
    assertEquals(4, route.totalCost(), 1e-9);
  }

  @Test
  void accessibleRouteNeverUsesStairsEvenIfTheyWereMarkedAccessible() {
    var markedStairs = link(LinkKind.STAIRS, 1, 1, Accessibility.ACCESSIBLE);

    CalculatedRoute route = route(List.of(markedStairs, ramp, elevator), new RouteConstraints(false, true, false));

    assertEquals(List.of(ramp.id()), linkIds(route));
    assertTrue(route.legs().stream().allMatch(leg -> leg.accessibility() == Accessibility.ACCESSIBLE));
  }

  @Test
  void accessibleRouteSkipsConnectionsWithUnconfirmedAccessibility() {
    var unconfirmedRamp = link(LinkKind.RAMP, 1, 1, Accessibility.UNKNOWN);

    CalculatedRoute route = route(List.of(unconfirmedRamp, elevator), new RouteConstraints(false, true, false));

    assertEquals(List.of(elevator.id()), linkIds(route));
  }

  @Test
  void noRouteWhenEveryConnectionBreaksARestriction() {
    var graph = graph(List.of(stairs));

    assertTrue(calculator.calculate(graph, lobby.id(), cardiology.id(),
        new RouteConstraints(true, false, false)).isEmpty());
    assertTrue(calculator.calculate(graph, lobby.id(), cardiology.id(),
        new RouteConstraints(false, true, false)).isEmpty());
    assertFalse(calculator.calculate(graph, lobby.id(), cardiology.id(), RouteConstraints.NONE).isEmpty());
  }

  @Test
  void theFormerAccessibilityFlagKeepsItsMeaning() {
    var graph = graph(List.of(stairs, ramp, elevator));

    assertEquals(linkIds(calculator.calculate(graph, lobby.id(), cardiology.id(), true).orElseThrow()),
        linkIds(route(List.of(stairs, ramp, elevator), RouteConstraints.accessibleOnly(true))));
    assertEquals(RouteConstraints.NONE, RouteConstraints.accessibleOnly(false));
  }

  @Test
  void combinedRestrictionsAreAtLeastAsStrictAsEachOne() {
    var combined = new RouteConstraints(true, false, false).and(new RouteConstraints(false, true, true));

    assertEquals(new RouteConstraints(true, true, true), combined);
    assertEquals(List.of(elevator.id()), linkIds(route(List.of(stairs, ramp, elevator), combined)));
  }

  private CalculatedRoute route(List<PublishedSpatialGraph.Link> links, RouteConstraints constraints) {
    return calculator.calculate(graph(links), lobby.id(), cardiology.id(), constraints).orElseThrow();
  }

  private PublishedSpatialGraph graph(List<PublishedSpatialGraph.Link> links) {
    return new PublishedSpatialGraph(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        List.of(new PublishedSpatialGraph.Plan(lowerFloor, lowerPlan, 100, 100, 1),
            new PublishedSpatialGraph.Plan(upperFloor, upperPlan, 100, 100, 1)),
        List.of(lobby, cardiology), links);
  }

  private static PublishedSpatialGraph.Node node(UUID floor, UUID plan, double x) {
    return new PublishedSpatialGraph.Node(UUID.randomUUID(), floor, plan, x, 0,
        PublishedSpatialGraph.NodeRole.VERTICAL_LINK);
  }

  private PublishedSpatialGraph.Link link(LinkKind kind, double weight, double penalty, Accessibility accessibility) {
    return new PublishedSpatialGraph.Link(UUID.randomUUID(), lobby.id(), cardiology.id(), kind, 5, weight, penalty,
        accessibility, true);
  }

  private static List<UUID> linkIds(CalculatedRoute route) {
    return route.legs().stream().map(CalculatedRoute.Leg::linkId).toList();
  }
}
