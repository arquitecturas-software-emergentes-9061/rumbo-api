package com.uitopic.rumbo.navigation.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * PR-224: regression contracts for route reachability and cross-floor route costs.
 * Current develop engine is Dijkstra; the persisted weighted A* algorithm from
 * feature/planning-coordinate-system must be validated separately after integration.
 */
class CrossFloorRouteContractTest {
  private final RouteCalculationDomainService engine = new RouteCalculationDomainService();

  @Test void choosesTheLowerCostOfTwoExplicitVerticalConnectors() {
    UUID lower = UUID.randomUUID(), upper = UUID.randomUUID(), site = UUID.randomUUID();
    var start = new RouteNode(lower, UUID.randomUUID(), "Entrance", 0, 0);
    var hall = new RouteNode(lower, UUID.randomUUID(), "Hall", 5, 0);
    var upstairs = new RouteNode(upper, UUID.randomUUID(), "Upper hall", 5, 0);
    var destination = new RouteNode(upper, UUID.randomUUID(), "Office", 8, 0);
    var graph = new PublishedRouteNetwork(site, List.of(start, hall, upstairs, destination), List.of(
      new RouteEdge(start.key(), upstairs.key(), 35, "elevator", true, false),
      new RouteEdge(start.key(), hall.key(), 5, "corridor", true, true),
      new RouteEdge(hall.key(), upstairs.key(), 7, "stairs", false, false),
      new RouteEdge(upstairs.key(), destination.key(), 3, "corridor", true, true)
    ), List.of());

    var shortest = engine.calculate(graph, start.key(), destination.key(), false);
    assertEquals(List.of(start, hall, upstairs, destination), shortest.nodes());
    assertEquals(15, shortest.totalDistanceMeters(), 1e-9);
    assertEquals(List.of("corridor", "stairs", "corridor"), shortest.edges().stream().map(RouteEdge::kind).toList());

    var accessible = engine.calculate(graph, start.key(), destination.key(), true);
    assertEquals(List.of(start, upstairs, destination), accessible.nodes());
    assertEquals(38, accessible.totalDistanceMeters(), 1e-9);
  }

  @Test void ignoresInvalidVerticalConnectionsAndNeverFabricatesAPath() {
    UUID first = UUID.randomUUID(), second = UUID.randomUUID(), site = UUID.randomUUID();
    var origin = new RouteNode(first, UUID.randomUUID(), "Origin", 0, 0);
    var target = new RouteNode(second, UUID.randomUUID(), "Target", 0, 0);
    var invalid = new PublishedRouteNetwork(site, List.of(origin, target), List.of(
      new RouteEdge(origin.key(), target.key(), 3, "corridor", true, true)
    ), List.of());
    assertThrows(NavigationValidationException.class,
      () -> engine.calculate(invalid, origin.key(), target.key(), false));

    var disconnected = new PublishedRouteNetwork(site, List.of(origin, target), List.of(), List.of());
    assertThrows(NavigationValidationException.class,
      () -> engine.calculate(disconnected, origin.key(), target.key(), false));
  }

  @Test void respectsDirectionOfCrossFloorConnection() {
    UUID first = UUID.randomUUID(), second = UUID.randomUUID(), site = UUID.randomUUID();
    var origin = new RouteNode(first, UUID.randomUUID(), "A", 0, 0);
    var target = new RouteNode(second, UUID.randomUUID(), "B", 0, 0);
    var graph = new PublishedRouteNetwork(site, List.of(origin, target),
      List.of(new RouteEdge(origin.key(), target.key(), 4, "elevator", true, false)), List.of());
    assertEquals(4, engine.calculate(graph, origin.key(), target.key(), false).totalDistanceMeters(), 1e-9);
    assertThrows(NavigationValidationException.class,
      () -> engine.calculate(graph, target.key(), origin.key(), false));
  }
}
