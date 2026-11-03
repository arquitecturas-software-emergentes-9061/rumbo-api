package com.uitopic.rumbo.navigation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Cost-optimality checks independent of the provisional demo route engine. */
class AStarRouteCalculationDomainServiceTest {
  private final AStarRouteCalculationDomainService calculator =
      new AStarRouteCalculationDomainService();
  private final UUID siteId = UUID.randomUUID();
  private final UUID mapId = UUID.randomUUID();
  private final UUID versionId = UUID.randomUUID();

  @Test
  void choosesCheaperWeightedDetourEvenWhenItsPhysicalDistanceIsLonger() {
    UUID floor = UUID.randomUUID();
    UUID plan = UUID.randomUUID();
    var a = node(floor, plan, 0);
    var b = node(floor, plan, 5);
    var c = node(floor, plan, 10);
    var graph = graph(List.of(space(floor, plan)), List.of(a, b, c), List.of(
        link(a, c, PublishedSpatialGraph.LinkKind.CORRIDOR, 10, 1, 0, false),
        link(a, b, PublishedSpatialGraph.LinkKind.CORRIDOR, 6, 0.2, 0, false),
        link(b, c, PublishedSpatialGraph.LinkKind.CORRIDOR, 6, 0.2, 0, false)));

    CalculatedRoute route = calculator.calculate(graph, a.id(), c.id()).orElseThrow();

    assertEquals(List.of(a.id(), b.id(), c.id()),
        route.nodes().stream().map(PublishedSpatialGraph.Node::id).toList());
    assertEquals(0.4, route.totalCost(), 1e-9);
    assertEquals(12, route.totalDistanceMeters(), 1e-9);
    assertEquals(versionId, route.modelVersionId());
  }

  @Test
  void choosesVerticalConnectorByWeightPlusPenaltyAndRespectsDirection() {
    UUID lower = UUID.randomUUID();
    UUID upper = UUID.randomUUID();
    UUID lowerPlan = UUID.randomUUID();
    UUID upperPlan = UUID.randomUUID();
    var start = node(lower, lowerPlan, 1);
    var goal = node(upper, upperPlan, 2);
    var elevator = link(start, goal, PublishedSpatialGraph.LinkKind.ELEVATOR,
        5, 1, 3, false);
    var stairs = link(start, goal, PublishedSpatialGraph.LinkKind.STAIRS,
        3, 0.5, 7, false);
    var graph = graph(List.of(space(lower, lowerPlan), space(upper, upperPlan)),
        List.of(start, goal), List.of(stairs, elevator));

    CalculatedRoute route = calculator.calculate(graph, start.id(), goal.id()).orElseThrow();

    assertEquals(4, route.totalCost(), 1e-9);
    assertEquals(5, route.totalDistanceMeters(), 1e-9);
    assertEquals(elevator.id(), route.legs().getFirst().linkId());
    assertEquals(3, route.legs().getFirst().verticalPenalty(), 1e-9);
    assertTrue(calculator.calculate(graph, goal.id(), start.id()).isEmpty());
  }

  @Test
  void returnsNoPartialRouteAndAllowsZeroCostRouteToSelf() {
    UUID floor = UUID.randomUUID();
    UUID plan = UUID.randomUUID();
    var a = node(floor, plan, 1);
    var b = node(floor, plan, 2);
    var graph = graph(List.of(space(floor, plan)), List.of(a, b), List.of());

    assertTrue(calculator.calculate(graph, a.id(), b.id()).isEmpty());
    CalculatedRoute same = calculator.calculate(graph, a.id(), a.id()).orElseThrow();
    assertEquals(0, same.totalCost());
    assertEquals(0, same.totalDistanceMeters());
    assertEquals(List.of(a), same.nodes());
    assertTrue(same.legs().isEmpty());
  }

  @Test
  void rejectsMissingNodesAndInvalidPublishedConnections() {
    UUID floor = UUID.randomUUID();
    UUID plan = UUID.randomUUID();
    var a = node(floor, plan, 1);
    var b = node(floor, plan, 2);
    var valid = graph(List.of(space(floor, plan)), List.of(a, b), List.of());
    assertThrows(IllegalArgumentException.class,
        () -> calculator.calculate(valid, a.id(), UUID.randomUUID()));
    var invalid = graph(List.of(space(floor, plan)), List.of(a, b), List.of(
        link(a, b, PublishedSpatialGraph.LinkKind.CORRIDOR, 1, 1, 2, true)));
    assertThrows(IllegalArgumentException.class,
        () -> calculator.calculate(invalid, a.id(), b.id()));
  }

  @Test
  void agreesWithBellmanFordOnDeterministicWeightedGraphs() {
    Random random = new Random(1206);
    UUID floor = UUID.randomUUID();
    UUID plan = UUID.randomUUID();
    List<PublishedSpatialGraph.Node> nodes = new ArrayList<>();
    for (int i = 0; i < 7; i++) nodes.add(node(floor, plan, i * 10));
    for (int sample = 0; sample < 50; sample++) {
      List<PublishedSpatialGraph.Link> links = new ArrayList<>();
      for (int from = 0; from < nodes.size(); from++) {
        for (int to = 0; to < nodes.size(); to++) {
          if (from == to || random.nextDouble() > 0.35) continue;
          links.add(link(nodes.get(from), nodes.get(to),
              PublishedSpatialGraph.LinkKind.CORRIDOR,
              Math.abs(from - to) * 10 + 1, 0.1 + random.nextDouble() * 20,
              0, false));
        }
      }
      var graph = graph(List.of(space(floor, plan)), nodes, links);
      double expected = bellmanFord(nodes, links);
      var actual = calculator.calculate(graph, nodes.getFirst().id(), nodes.getLast().id());
      if (Double.isInfinite(expected)) {
        assertFalse(actual.isPresent(), "sample " + sample);
      } else {
        assertEquals(expected, actual.orElseThrow().totalCost(), 1e-8, "sample " + sample);
      }
    }
  }

  private double bellmanFord(List<PublishedSpatialGraph.Node> nodes,
                             List<PublishedSpatialGraph.Link> links) {
    java.util.Map<UUID, Double> cost = new java.util.HashMap<>();
    nodes.forEach(node -> cost.put(node.id(), Double.POSITIVE_INFINITY));
    cost.put(nodes.getFirst().id(), 0.0);
    for (int pass = 1; pass < nodes.size(); pass++) {
      for (PublishedSpatialGraph.Link link : links) {
        double candidate = cost.get(link.sourceNodeId()) + link.weight();
        if (candidate < cost.get(link.targetNodeId())) {
          cost.put(link.targetNodeId(), candidate);
        }
      }
    }
    return cost.get(nodes.getLast().id());
  }

  private PublishedSpatialGraph graph(List<PublishedSpatialGraph.Plan> plans,
      List<PublishedSpatialGraph.Node> nodes, List<PublishedSpatialGraph.Link> links) {
    return new PublishedSpatialGraph(siteId, mapId, versionId, plans, nodes, links);
  }

  private PublishedSpatialGraph.Plan space(UUID floor, UUID plan) {
    return new PublishedSpatialGraph.Plan(floor, plan, 100, 100, 1);
  }

  private PublishedSpatialGraph.Node node(UUID floor, UUID plan, double x) {
    return new PublishedSpatialGraph.Node(UUID.randomUUID(), floor, plan, x, 0,
        PublishedSpatialGraph.NodeRole.INTERSECTION);
  }

  private PublishedSpatialGraph.Link link(PublishedSpatialGraph.Node from,
      PublishedSpatialGraph.Node to, PublishedSpatialGraph.LinkKind kind,
      double distanceMeters, double weight, double penalty, boolean bidirectional) {
    return new PublishedSpatialGraph.Link(UUID.randomUUID(), from.id(), to.id(), kind,
        distanceMeters, weight, penalty, bidirectional);
  }
}
