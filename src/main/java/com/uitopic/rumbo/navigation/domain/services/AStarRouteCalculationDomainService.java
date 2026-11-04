package com.uitopic.rumbo.navigation.domain.services;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.CalculatedRoute;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedSpatialGraph;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

/** Finds a minimum-cost route using persisted weights and vertical penalties. */
public final class AStarRouteCalculationDomainService {
  private record Traversal(UUID from, UUID to, PublishedSpatialGraph.Link link) {
    double cost() { return link.weight() + link.verticalPenalty(); }
  }

  private record Candidate(UUID nodeId, double cost, double estimatedTotal) {}

  /**
   * Calculates a route in one published version; an unreachable goal returns empty.
   * The Euclidean heuristic is scaled to a proven cost lower bound only for a graph
   * whose nodes share one floor-plan coordinate system. Other graphs use zero.
   */
  public Optional<CalculatedRoute> calculate(PublishedSpatialGraph graph,
                                             UUID fromNodeId, UUID toNodeId) {
    Objects.requireNonNull(graph, "graph is required");
    Objects.requireNonNull(fromNodeId, "fromNodeId is required");
    Objects.requireNonNull(toNodeId, "toNodeId is required");
    Map<UUID, PublishedSpatialGraph.Plan> plans = plansById(graph);
    Map<UUID, PublishedSpatialGraph.Node> nodes = nodesById(graph, plans);
    Map<UUID, List<Traversal>> adjacency = adjacency(graph, nodes);
    if (!nodes.containsKey(fromNodeId) || !nodes.containsKey(toNodeId)) {
      throw new IllegalArgumentException("Both route nodes must belong to the published model");
    }
    if (fromNodeId.equals(toNodeId)) {
      return Optional.of(new CalculatedRoute(graph.siteId(), graph.modelVersionId(),
          List.of(nodes.get(fromNodeId)), List.of(), 0, 0));
    }

    double costPerMeter = admissibleCostPerMeter(graph, plans, nodes);
    PublishedSpatialGraph.Node goal = nodes.get(toNodeId);
    Map<UUID, Double> best = new HashMap<>();
    Map<UUID, Traversal> previous = new HashMap<>();
    PriorityQueue<Candidate> pending = new PriorityQueue<>(Comparator
        .comparingDouble(Candidate::estimatedTotal)
        .thenComparingDouble(Candidate::cost).thenComparing(Candidate::nodeId));
    best.put(fromNodeId, 0.0);
    pending.add(new Candidate(fromNodeId, 0,
        heuristic(nodes.get(fromNodeId), goal, plans, costPerMeter)));

    while (!pending.isEmpty()) {
      Candidate current = pending.remove();
      if (current.cost() > best.getOrDefault(current.nodeId(), Double.POSITIVE_INFINITY)) continue;
      if (current.nodeId().equals(toNodeId)) {
        return Optional.of(reconstruct(graph, nodes, previous, fromNodeId, toNodeId,
            current.cost()));
      }
      for (Traversal traversal : adjacency.get(current.nodeId())) {
        double nextCost = current.cost() + traversal.cost();
        if (!Double.isFinite(nextCost)) {
          throw new IllegalArgumentException("Accumulated route cost is not finite");
        }
        if (nextCost >= best.getOrDefault(traversal.to(), Double.POSITIVE_INFINITY)) continue;
        best.put(traversal.to(), nextCost);
        previous.put(traversal.to(), traversal);
        double estimate = nextCost + heuristic(nodes.get(traversal.to()), goal,
            plans, costPerMeter);
        pending.add(new Candidate(traversal.to(), nextCost,
            Double.isFinite(estimate) ? estimate : nextCost));
      }
    }
    return Optional.empty();
  }

  private Map<UUID, PublishedSpatialGraph.Plan> plansById(PublishedSpatialGraph graph) {
    Map<UUID, PublishedSpatialGraph.Plan> plans = new HashMap<>();
    for (PublishedSpatialGraph.Plan plan : graph.plans()) {
      if (plan == null || plan.floorId() == null || plan.planVersionId() == null
          || !Double.isFinite(plan.widthUnits()) || plan.widthUnits() <= 0
          || !Double.isFinite(plan.heightUnits()) || plan.heightUnits() <= 0
          || !Double.isFinite(plan.metersPerUnit()) || plan.metersPerUnit() <= 0
          || plans.putIfAbsent(plan.planVersionId(), plan) != null) {
        throw new IllegalArgumentException("Published plan coordinate system is invalid");
      }
    }
    return plans;
  }

  private Map<UUID, PublishedSpatialGraph.Node> nodesById(PublishedSpatialGraph graph,
      Map<UUID, PublishedSpatialGraph.Plan> plans) {
    Map<UUID, PublishedSpatialGraph.Node> nodes = new HashMap<>();
    for (PublishedSpatialGraph.Node node : graph.nodes()) {
      if (node == null || node.id() == null || node.floorId() == null
          || node.planVersionId() == null || node.role() == null
          || !Double.isFinite(node.x()) || !Double.isFinite(node.y())) {
        throw new IllegalArgumentException("Published node is invalid");
      }
      PublishedSpatialGraph.Plan plan = plans.get(node.planVersionId());
      if (plan == null || !plan.floorId().equals(node.floorId())
          || node.x() < 0 || node.y() < 0
          || node.x() > plan.widthUnits() || node.y() > plan.heightUnits()
          || nodes.putIfAbsent(node.id(), node) != null) {
        throw new IllegalArgumentException("Published node is outside its plan or duplicated");
      }
    }
    return nodes;
  }

  private Map<UUID, List<Traversal>> adjacency(PublishedSpatialGraph graph,
      Map<UUID, PublishedSpatialGraph.Node> nodes) {
    Map<UUID, List<Traversal>> adjacency = new HashMap<>();
    nodes.keySet().forEach(id -> adjacency.put(id, new ArrayList<>()));
    Set<UUID> linkIds = new HashSet<>();
    for (PublishedSpatialGraph.Link link : graph.links()) {
      if (link == null || link.id() == null || link.sourceNodeId() == null
          || link.targetNodeId() == null || link.kind() == null
          || !linkIds.add(link.id()) || link.sourceNodeId().equals(link.targetNodeId())
          || !Double.isFinite(link.distanceMeters()) || link.distanceMeters() <= 0
          || !Double.isFinite(link.weight()) || link.weight() <= 0
          || !Double.isFinite(link.verticalPenalty()) || link.verticalPenalty() < 0
          || !Double.isFinite(link.weight() + link.verticalPenalty())) {
        throw new IllegalArgumentException("Published connection is invalid");
      }
      PublishedSpatialGraph.Node source = nodes.get(link.sourceNodeId());
      PublishedSpatialGraph.Node target = nodes.get(link.targetNodeId());
      if (source == null || target == null) {
        throw new IllegalArgumentException("Published connection references an unknown node");
      }
      boolean crossesFloor = !source.floorId().equals(target.floorId());
      if (crossesFloor && link.kind() != PublishedSpatialGraph.LinkKind.STAIRS
          && link.kind() != PublishedSpatialGraph.LinkKind.RAMP
          && link.kind() != PublishedSpatialGraph.LinkKind.ELEVATOR) {
        throw new IllegalArgumentException("Cross-floor connection is not vertical");
      }
      if (!crossesFloor && link.verticalPenalty() != 0) {
        throw new IllegalArgumentException("Same-floor connection has a vertical penalty");
      }
      adjacency.get(link.sourceNodeId()).add(new Traversal(link.sourceNodeId(),
          link.targetNodeId(), link));
      if (link.bidirectional()) {
        adjacency.get(link.targetNodeId()).add(new Traversal(link.targetNodeId(),
            link.sourceNodeId(), link));
      }
    }
    return adjacency;
  }

  private double admissibleCostPerMeter(PublishedSpatialGraph graph,
      Map<UUID, PublishedSpatialGraph.Plan> plans,
      Map<UUID, PublishedSpatialGraph.Node> nodes) {
    if (nodes.isEmpty()) return 0;
    PublishedSpatialGraph.Node first = nodes.values().iterator().next();
    if (nodes.values().stream().anyMatch(node -> !node.floorId().equals(first.floorId())
        || !node.planVersionId().equals(first.planVersionId()))) return 0;
    double scale = plans.get(first.planVersionId()).metersPerUnit();
    double minimum = Double.POSITIVE_INFINITY;
    for (PublishedSpatialGraph.Link link : graph.links()) {
      PublishedSpatialGraph.Node source = nodes.get(link.sourceNodeId());
      PublishedSpatialGraph.Node target = nodes.get(link.targetNodeId());
      double meters = Math.hypot(source.x() - target.x(), source.y() - target.y()) * scale;
      if (!Double.isFinite(meters)) return 0;
      if (meters > 0) minimum = Math.min(minimum,
          (link.weight() + link.verticalPenalty()) / meters);
    }
    return Double.isFinite(minimum) ? minimum : 0;
  }

  private double heuristic(PublishedSpatialGraph.Node current,
      PublishedSpatialGraph.Node goal, Map<UUID, PublishedSpatialGraph.Plan> plans,
      double costPerMeter) {
    if (costPerMeter == 0) return 0;
    double scale = plans.get(current.planVersionId()).metersPerUnit();
    double estimate = Math.hypot(current.x() - goal.x(), current.y() - goal.y())
        * scale * costPerMeter;
    return Double.isFinite(estimate) ? estimate : 0;
  }

  private CalculatedRoute reconstruct(PublishedSpatialGraph graph,
      Map<UUID, PublishedSpatialGraph.Node> nodes, Map<UUID, Traversal> previous,
      UUID start, UUID goal, double totalCost) {
    List<PublishedSpatialGraph.Node> orderedNodes = new ArrayList<>();
    List<CalculatedRoute.Leg> orderedLegs = new ArrayList<>();
    double totalDistance = 0;
    UUID cursor = goal;
    orderedNodes.add(nodes.get(cursor));
    while (!cursor.equals(start)) {
      Traversal traversal = previous.get(cursor);
      if (traversal == null) throw new IllegalStateException("Route predecessor is missing");
      PublishedSpatialGraph.Link link = traversal.link();
      orderedLegs.add(new CalculatedRoute.Leg(link.id(), traversal.from(), traversal.to(),
          link.kind(), link.distanceMeters(), link.weight(), link.verticalPenalty()));
      totalDistance += link.distanceMeters();
      cursor = traversal.from();
      orderedNodes.add(nodes.get(cursor));
    }
    Collections.reverse(orderedNodes);
    Collections.reverse(orderedLegs);
    return new CalculatedRoute(graph.siteId(), graph.modelVersionId(), orderedNodes,
        orderedLegs, totalDistance, totalCost);
  }
}
