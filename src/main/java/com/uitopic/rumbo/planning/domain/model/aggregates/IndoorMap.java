package com.uitopic.rumbo.planning.domain.model.aggregates;

import com.uitopic.rumbo.planning.domain.exceptions.InvalidMapGraphException;
import com.uitopic.rumbo.planning.domain.model.entities.MapConnection;
import com.uitopic.rumbo.planning.domain.model.entities.MapNode;
import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphIssue;
import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphIssueCode;
import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphElementType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphValidationResult;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapScale;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PlanCoordinateSpace;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * Planning aggregate root for editing one draft version of a site's indoor map.
 * Publication status, version history, persistence, and external contracts are
 * introduced by later use cases; graph edits must pass through this boundary.
 */
public final class IndoorMap {
  private final UUID id;
  private final UUID siteId;
  private final UUID draftVersionId;
  private final Map<UUID, PlanCoordinateSpace> plans = new LinkedHashMap<>();
  private final Map<UUID, MapScale> scales = new LinkedHashMap<>();
  private final Map<UUID, MapNode> nodes = new LinkedHashMap<>();
  private final Map<UUID, MapConnection> connections = new LinkedHashMap<>();

  /** Starts an empty draft graph for one site and model version. */
  public IndoorMap(UUID id, UUID siteId, UUID draftVersionId) {
    this.id = Objects.requireNonNull(id, "id is required");
    this.siteId = Objects.requireNonNull(siteId, "siteId is required");
    this.draftVersionId = Objects.requireNonNull(draftVersionId, "draftVersionId is required");
  }

  public UUID id() { return id; }
  public UUID siteId() { return siteId; }
  public UUID draftVersionId() { return draftVersionId; }

  /** Read-only snapshot of the plan coordinate spaces used by this draft. */
  public List<PlanCoordinateSpace> plans() { return List.copyOf(plans.values()); }

  /** Read-only snapshot of confirmed physical scales in this draft. */
  public List<MapScale> scales() { return List.copyOf(scales.values()); }

  /** Read-only snapshot of nodes in this draft. */
  public List<MapNode> nodes() { return List.copyOf(nodes.values()); }

  /** Read-only snapshot of connections in this draft. */
  public List<MapConnection> connections() { return List.copyOf(connections.values()); }

  /** Registers the bounded coordinate system of one floor-plan version. */
  public void addPlan(PlanCoordinateSpace space) {
    Objects.requireNonNull(space, "space is required");
    if (plans.containsKey(space.planVersionId())) {
      reject(GraphIssueCode.DUPLICATE_PLAN_VERSION, GraphElementType.PLAN,
          space.planVersionId(), "Plan version is already present");
    }
    plans.put(space.planVersionId(), space);
  }

  /** Confirms or replaces the physical scale of a registered floor-plan version. */
  public void setScale(MapScale scale) {
    Objects.requireNonNull(scale, "scale is required");
    PlanCoordinateSpace space = plans.get(scale.planVersionId());
    if (space == null) {
      reject(GraphIssueCode.MISSING_PLAN_VERSION, GraphElementType.PLAN,
          scale.planVersionId(), "Scale refers to an unregistered plan version");
    }
    if (!space.floorId().equals(scale.floorId())) {
      reject(GraphIssueCode.PLAN_FLOOR_MISMATCH, GraphElementType.PLAN,
          scale.planVersionId(), "Scale floor differs from the registered plan floor");
    }
    scales.put(scale.planVersionId(), scale);
  }

  /** Adds a node whose identity belongs to this draft model version. */
  public void addNode(MapNode node) {
    Objects.requireNonNull(node, "node is required");
    if (!draftVersionId.equals(node.modelVersionId())) {
      reject(GraphIssueCode.WRONG_MODEL_VERSION, GraphElementType.NODE, node.id(), "Node belongs to another model version");
    }
    if (nodes.containsKey(node.id())) {
      reject(GraphIssueCode.DUPLICATE_NODE_ID, GraphElementType.NODE, node.id(), "Node ID is already present");
    }
    PlanCoordinateSpace space = plans.get(node.position().planVersionId());
    if (space == null) {
      reject(GraphIssueCode.MISSING_PLAN_VERSION, GraphElementType.NODE, node.id(),
          "Node refers to an unregistered plan version");
    }
    if (!space.contains(node.position())) {
      reject(GraphIssueCode.PLAN_FLOOR_MISMATCH, GraphElementType.NODE, node.id(),
          "Node position does not belong to the registered floor-plan space");
    }
    nodes.put(node.id(), node);
  }

  /**
   * Adds a connection after checking its version, endpoints, and overlapping links.
   * Failed edits leave the draft unchanged.
   */
  public void addConnection(MapConnection connection) {
    Objects.requireNonNull(connection, "connection is required");
    if (!draftVersionId.equals(connection.modelVersionId())) {
      reject(GraphIssueCode.WRONG_MODEL_VERSION, GraphElementType.CONNECTION, connection.id(),
          "Connection belongs to another model version");
    }
    if (connections.containsKey(connection.id())) {
      reject(GraphIssueCode.DUPLICATE_CONNECTION_ID, GraphElementType.CONNECTION, connection.id(), "Connection ID is already present");
    }
    if (!nodes.containsKey(connection.sourceNodeId())) {
      reject(GraphIssueCode.MISSING_SOURCE_NODE, GraphElementType.CONNECTION, connection.id(), "Connection source node is missing");
    }
    if (!nodes.containsKey(connection.targetNodeId())) {
      reject(GraphIssueCode.MISSING_TARGET_NODE, GraphElementType.CONNECTION, connection.id(), "Connection target node is missing");
    }
    if (connections.values().stream().anyMatch(existing -> overlaps(existing, connection))) {
      reject(GraphIssueCode.DUPLICATE_CONNECTION, GraphElementType.CONNECTION, connection.id(),
          "Connection repeats a traversable link of the same type");
    }
    connections.put(connection.id(), connection);
  }

  /** Validates the current draft against the rules required before publication. */
  public GraphValidationResult validateForPublication() {
    List<GraphIssue> issues = new ArrayList<>();
    if (nodes.isEmpty()) {
      issues.add(issue(GraphIssueCode.EMPTY_GRAPH, GraphElementType.GRAPH, null, "Graph has no nodes"));
    }
    Set<UUID> accesses = new LinkedHashSet<>();
    Set<UUID> destinations = new LinkedHashSet<>();
    for (MapNode node : nodes.values()) {
      if (node.type() == NodeType.ACCESS) accesses.add(node.id());
      if (node.type() == NodeType.DESTINATION) destinations.add(node.id());
    }
    if (accesses.isEmpty()) {
      issues.add(issue(GraphIssueCode.MISSING_ACCESS, GraphElementType.GRAPH, null, "Graph has no access node"));
    }
    if (destinations.isEmpty()) {
      issues.add(issue(GraphIssueCode.MISSING_DESTINATION, GraphElementType.GRAPH, null, "Graph has no destination node"));
    }

    Map<UUID, Set<UUID>> undirected = adjacency();
    Map<UUID, Set<UUID>> directed = adjacency();
    for (MapConnection connection : connections.values()) {
      undirected.get(connection.sourceNodeId()).add(connection.targetNodeId());
      undirected.get(connection.targetNodeId()).add(connection.sourceNodeId());
      directed.get(connection.sourceNodeId()).add(connection.targetNodeId());
      if (connection.bidirectional()) {
        directed.get(connection.targetNodeId()).add(connection.sourceNodeId());
      }
    }
    if (!accesses.isEmpty()) {
      Set<UUID> connected = reachable(Set.of(accesses.iterator().next()), undirected);
      for (UUID nodeId : nodes.keySet()) {
        if (!connected.contains(nodeId)) {
          issues.add(issue(GraphIssueCode.DISCONNECTED_NODE, GraphElementType.NODE, nodeId,
              "Node is outside the connected graph of an access node"));
        }
      }
      Set<UUID> reachable = reachable(accesses, directed);
      for (UUID destinationId : destinations) {
        if (connected.contains(destinationId) && !reachable.contains(destinationId)) {
          issues.add(issue(GraphIssueCode.UNREACHABLE_DESTINATION, GraphElementType.NODE, destinationId,
              "Destination cannot be reached from any access node"));
        }
      }
    }
    return new GraphValidationResult(issues);
  }

  private boolean overlaps(MapConnection first, MapConnection second) {
    if (first.type() != second.type()) return false;
    boolean same = first.sourceNodeId().equals(second.sourceNodeId())
        && first.targetNodeId().equals(second.targetNodeId());
    boolean reverse = first.sourceNodeId().equals(second.targetNodeId())
        && first.targetNodeId().equals(second.sourceNodeId());
    return same || (reverse && (first.bidirectional() || second.bidirectional()));
  }

  private Map<UUID, Set<UUID>> adjacency() {
    Map<UUID, Set<UUID>> links = new HashMap<>();
    nodes.keySet().forEach(id -> links.put(id, new HashSet<>()));
    return links;
  }

  private Set<UUID> reachable(Set<UUID> starts, Map<UUID, Set<UUID>> links) {
    Set<UUID> visited = new HashSet<>(starts);
    Queue<UUID> pending = new ArrayDeque<>(starts);
    while (!pending.isEmpty()) {
      for (UUID next : links.get(pending.remove())) {
        if (visited.add(next)) pending.add(next);
      }
    }
    return visited;
  }

  private GraphIssue issue(GraphIssueCode code, GraphElementType elementType,
                           UUID elementId, String message) {
    return new GraphIssue(code, elementType, elementId, message);
  }

  private void reject(GraphIssueCode code, GraphElementType elementType, UUID elementId, String message) {
    throw new InvalidMapGraphException(new GraphIssue(code, elementType, elementId, message));
  }
}
