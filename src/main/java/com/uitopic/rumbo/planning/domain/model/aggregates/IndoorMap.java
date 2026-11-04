package com.uitopic.rumbo.planning.domain.model.aggregates;

import com.uitopic.rumbo.planning.domain.exceptions.InvalidMapGraphException;
import com.uitopic.rumbo.planning.domain.exceptions.InvalidMapVersionStateException;
import com.uitopic.rumbo.planning.domain.exceptions.MapPublicationRejectedException;
import com.uitopic.rumbo.planning.domain.model.entities.MapConnection;
import com.uitopic.rumbo.planning.domain.model.entities.MapDestination;
import com.uitopic.rumbo.planning.domain.model.entities.MapNode;
import com.uitopic.rumbo.planning.domain.model.entities.MapVersion;
import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphIssue;
import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphIssueCode;
import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphElementType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphValidationResult;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.DirectoryEntryType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapScale;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PlanCoordinateSpace;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapVersionStatus;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionAccessibility;
import com.uitopic.rumbo.planning.domain.model.valueobjects.SpatialCoordinate;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PublishedMapSnapshot;
import com.uitopic.rumbo.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
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
 * Planning aggregate root for one version of a site's indoor map.
 * It controls draft edits and the transition to an immutable published graph;
 * persistence and version history are coordinated outside the domain.
 */
public final class IndoorMap extends AbstractDomainAggregateRoot<UUID> {
  private final UUID id;
  private final UUID siteId;
  private final MapVersion version;
  private long editRevision;
  private final Map<UUID, PlanCoordinateSpace> plans = new LinkedHashMap<>();
  private final Map<UUID, MapScale> scales = new LinkedHashMap<>();
  private final Map<UUID, MapNode> nodes = new LinkedHashMap<>();
  private final Map<UUID, MapConnection> connections = new LinkedHashMap<>();
  private final Map<UUID, MapDestination> destinations = new LinkedHashMap<>();

  /** Starts an empty draft graph for one site and model version. */
  public IndoorMap(UUID id, UUID siteId, UUID modelVersionId) {
    this(id, siteId, modelVersionId, 0);
  }

  /** Reconstitutes a draft at its persisted edit revision. */
  public IndoorMap(UUID id, UUID siteId, UUID modelVersionId, long editRevision) {
    this.id = Objects.requireNonNull(id, "id is required");
    this.siteId = Objects.requireNonNull(siteId, "siteId is required");
    this.version = new MapVersion(modelVersionId, MapVersionStatus.DRAFT);
    if (editRevision < 0) throw new IllegalArgumentException("editRevision must be nonnegative");
    this.editRevision = editRevision;
  }

  /**
   * Creates an independent draft from a published graph while retaining stable element IDs.
   * Every copied element is rebound to the new model version and checked by this aggregate.
   */
  public static IndoorMap draftFromPublished(PublishedMapSnapshot source, UUID newVersionId) {
    Objects.requireNonNull(source, "source is required");
    Objects.requireNonNull(newVersionId, "newVersionId is required");
    if (newVersionId.equals(source.modelVersionId())) {
      throw new IllegalArgumentException("New draft needs a different model version ID");
    }
    IndoorMap draft = new IndoorMap(source.mapId(), source.siteId(), newVersionId);
    for (PublishedMapSnapshot.Plan plan : source.plans()) {
      draft.addPlan(new PlanCoordinateSpace(plan.floorId(), plan.planVersionId(),
          plan.widthUnits(), plan.heightUnits()));
      draft.setScale(new MapScale(plan.floorId(), plan.planVersionId(), plan.metersPerUnit()));
    }
    for (PublishedMapSnapshot.Node node : source.nodes()) {
      draft.addNode(node.id(), node.floorId(), node.planVersionId(),
          node.x(), node.y(), node.type());
    }
    for (PublishedMapSnapshot.Connection connection : source.connections()) {
      draft.addConnection(connection.id(), connection.sourceNodeId(),
          connection.targetNodeId(), connection.type(), connection.distanceMeters(),
          connection.weight(), connection.verticalPenalty(), connection.accessibility(),
          connection.bidirectional());
    }
    for (PublishedMapSnapshot.Destination destination : source.destinations()) {
      draft.addDestination(destination.id(), destination.nodeId(), destination.type(),
          destination.name(), destination.specialty());
    }
    GraphValidationResult validation = draft.validateForPublication();
    if (!validation.isValid()) throw new MapPublicationRejectedException(validation);
    return draft;
  }

  @Override public UUID id() { return id; }
  public UUID siteId() { return siteId; }
  public UUID modelVersionId() { return version.id(); }
  public MapVersionStatus status() { return version.status(); }
  public long editRevision() { return editRevision; }

  /** Advances the draft revision after its previous value has been checked for a save. */
  public void advanceEditRevision() {
    requireDraft();
    editRevision = Math.addExact(editRevision, 1);
  }

  /** Read-only snapshot of the plan coordinate spaces used by this draft. */
  public List<PlanCoordinateSpace> plans() { return List.copyOf(plans.values()); }

  /** Read-only snapshot of confirmed physical scales in this draft. */
  public List<MapScale> scales() { return List.copyOf(scales.values()); }

  /** Read-only snapshot of nodes in this draft. */
  public List<MapNode> nodes() { return List.copyOf(nodes.values()); }

  /** Read-only snapshot of connections in this draft. */
  public List<MapConnection> connections() { return List.copyOf(connections.values()); }

  /** Read-only snapshot of medical entries tied to this version's destination nodes. */
  public List<MapDestination> destinations() { return List.copyOf(destinations.values()); }

  /** Adds one medical entry only when its node is a destination in this version. */
  public void addDestination(MapDestination destination) {
    requireDraft();
    Objects.requireNonNull(destination, "destination is required");
    if (!modelVersionId().equals(destination.modelVersionId())) {
      reject(GraphIssueCode.WRONG_MODEL_VERSION, GraphElementType.DESTINATION,
          destination.id(), "Destination belongs to another model version");
    }
    if (destinations.containsKey(destination.id())) {
      reject(GraphIssueCode.DUPLICATE_DESTINATION_ID, GraphElementType.DESTINATION,
          destination.id(), "Destination ID is already present");
    }
    MapNode node = nodes.get(destination.nodeId());
    if (node == null) {
      reject(GraphIssueCode.MISSING_DESTINATION_NODE, GraphElementType.DESTINATION,
          destination.id(), "Destination node is missing");
    }
    if (node.type() != NodeType.DESTINATION) {
      reject(GraphIssueCode.INVALID_DESTINATION_NODE_TYPE, GraphElementType.DESTINATION,
          destination.id(), "Entry must refer to a destination node");
    }
    destinations.put(destination.id(), destination);
  }

  /** Adds an editor entry and reports invalid metadata at its identifier. */
  public void addDestination(UUID id, UUID nodeId, DirectoryEntryType type,
                             String name, String specialty) {
    MapDestination destination;
    try {
      destination = new MapDestination(id, modelVersionId(), nodeId, type, name, specialty);
    } catch (IllegalArgumentException | NullPointerException error) {
      reject(GraphIssueCode.INVALID_DESTINATION_DATA, GraphElementType.DESTINATION,
          id, "Destination identity, type, or label is invalid");
      return;
    }
    addDestination(destination);
  }

  /** Registers the bounded coordinate system of one floor-plan version. */
  public void addPlan(PlanCoordinateSpace space) {
    requireDraft();
    Objects.requireNonNull(space, "space is required");
    if (plans.containsKey(space.planVersionId())) {
      reject(GraphIssueCode.DUPLICATE_PLAN_VERSION, GraphElementType.PLAN,
          space.planVersionId(), "Plan version is already present");
    }
    plans.put(space.planVersionId(), space);
  }

  /** Confirms or replaces the physical scale of a registered floor-plan version. */
  public void setScale(MapScale scale) {
    requireDraft();
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
    requireDraft();
    Objects.requireNonNull(node, "node is required");
    if (!modelVersionId().equals(node.modelVersionId())) {
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

  /** Adds a node from editor coordinates, reporting invalid geometry at the node ID. */
  public void addNode(UUID nodeId, UUID floorId, UUID planVersionId,
                      double x, double y, NodeType type) {
    requireDraft();
    PlanCoordinateSpace space = plans.get(planVersionId);
    if (space == null) {
      reject(GraphIssueCode.MISSING_PLAN_VERSION, GraphElementType.NODE, nodeId,
          "Node refers to an unregistered plan version");
    }
    MapNode node;
    try {
      node = new MapNode(nodeId, modelVersionId(),
          new SpatialCoordinate(floorId, planVersionId, x, y), type, space);
    } catch (IllegalArgumentException | NullPointerException error) {
      reject(GraphIssueCode.INVALID_NODE_DATA, GraphElementType.NODE, nodeId,
          "Node position or type is invalid for its floor-plan version");
      return;
    }
    addNode(node);
  }

  /**
   * Adds a connection after checking its version, endpoints, and overlapping links.
   * Failed edits leave the draft unchanged.
   */
  public void addConnection(MapConnection connection) {
    requireDraft();
    Objects.requireNonNull(connection, "connection is required");
    if (!modelVersionId().equals(connection.modelVersionId())) {
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
    MapNode source = nodes.get(connection.sourceNodeId());
    MapNode target = nodes.get(connection.targetNodeId());
    boolean crossesFloor = !source.position().floorId().equals(target.position().floorId());
    if (crossesFloor
        && connection.type() != ConnectionType.STAIRS
        && connection.type() != ConnectionType.RAMP
        && connection.type() != ConnectionType.ELEVATOR) {
      reject(GraphIssueCode.INVALID_VERTICAL_CONNECTION, GraphElementType.CONNECTION,
          connection.id(), "Cross-floor connection must be stairs, ramp, or elevator");
    }
    if (!crossesFloor && connection.verticalPenalty() != 0) {
      reject(GraphIssueCode.INVALID_CONNECTION_DATA, GraphElementType.CONNECTION,
          connection.id(), "Vertical penalty requires a cross-floor connection");
    }
    if (connections.values().stream().anyMatch(existing -> overlaps(existing, connection))) {
      reject(GraphIssueCode.DUPLICATE_CONNECTION, GraphElementType.CONNECTION, connection.id(),
          "Connection repeats a traversable link of the same type");
    }
    connections.put(connection.id(), connection);
  }

  /** Adds a weighted editor connection, reporting invalid metrics at its ID. */
  public void addConnection(UUID connectionId, UUID sourceNodeId, UUID targetNodeId,
                            ConnectionType type, double distanceMeters, double weight,
                            double verticalPenalty, ConnectionAccessibility accessibility,
                            boolean bidirectional) {
    requireDraft();
    MapConnection connection;
    try {
      connection = new MapConnection(connectionId, modelVersionId(), sourceNodeId,
          targetNodeId, type, distanceMeters, weight, verticalPenalty, accessibility,
          bidirectional);
    } catch (IllegalArgumentException | NullPointerException error) {
      reject(GraphIssueCode.INVALID_CONNECTION_DATA, GraphElementType.CONNECTION, connectionId,
          "Connection endpoints, type, distance, or weight are invalid");
      return;
    }
    addConnection(connection);
  }

  /** Adds a connection whose accessibility has not yet been classified. */
  public void addConnection(UUID connectionId, UUID sourceNodeId, UUID targetNodeId,
                            ConnectionType type, double distanceMeters, double weight,
                            double verticalPenalty, boolean bidirectional) {
    addConnection(connectionId, sourceNodeId, targetNodeId, type, distanceMeters, weight,
        verticalPenalty, ConnectionAccessibility.UNKNOWN, bidirectional);
  }

  /** Adds a legacy editor connection with no separate vertical penalty. */
  public void addConnection(UUID connectionId, UUID sourceNodeId, UUID targetNodeId,
                            ConnectionType type, double distanceMeters, double weight,
                            boolean bidirectional) {
    addConnection(connectionId, sourceNodeId, targetNodeId, type,
        distanceMeters, weight, 0, bidirectional);
  }

  /** Validates the current draft against the rules required before publication. */
  public GraphValidationResult validateForPublication() {
    List<GraphIssue> issues = new ArrayList<>();
    for (PlanCoordinateSpace space : plans.values()) {
      if (!scales.containsKey(space.planVersionId())) {
        issues.add(issue(GraphIssueCode.MISSING_SCALE, GraphElementType.PLAN,
            space.planVersionId(), "Plan version has no confirmed physical scale"));
      }
    }
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

  /** Publishes this draft only when every graph issue has been resolved. */
  public void publish() {
    requireDraft();
    GraphValidationResult validation = validateForPublication();
    if (!validation.isValid()) throw new MapPublicationRejectedException(validation);
    version.publish();
  }

  private void requireDraft() {
    if (status() != MapVersionStatus.DRAFT) throw new InvalidMapVersionStateException(status());
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
