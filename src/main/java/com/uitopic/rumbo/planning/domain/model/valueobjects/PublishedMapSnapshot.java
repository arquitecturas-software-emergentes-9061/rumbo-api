package com.uitopic.rumbo.planning.domain.model.valueobjects;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable, versioned read model of a published Planning map. */
public record PublishedMapSnapshot(UUID siteId, UUID mapId, UUID modelVersionId,
                                   List<Plan> plans, List<Node> nodes,
                                   List<Connection> connections, List<Destination> destinations) {
  public PublishedMapSnapshot {
    Objects.requireNonNull(siteId, "siteId is required");
    Objects.requireNonNull(mapId, "mapId is required");
    Objects.requireNonNull(modelVersionId, "modelVersionId is required");
    plans = List.copyOf(plans);
    nodes = List.copyOf(nodes);
    connections = List.copyOf(connections);
    destinations = List.copyOf(destinations);
  }

  /** Coordinate frame and confirmed physical scale of one floor-plan version. */
  public record Plan(UUID floorId, UUID planVersionId, double widthUnits,
                     double heightUnits, double metersPerUnit) {}

  /** Navigable node whose coordinates remain in its floor-plan units. */
  public record Node(UUID id, UUID floorId, UUID planVersionId,
                     double x, double y, NodeType type) {}

  /** Persisted link with independent physical distance and routing weight. */
  public record Connection(UUID id, UUID sourceNodeId, UUID targetNodeId,
                           ConnectionType type, double distanceMeters,
                           double weight, double verticalPenalty,
                           boolean bidirectional) {}

  /** Medical entry attached to a node in this published version. */
  public record Destination(UUID id, UUID modelVersionId, UUID nodeId, UUID floorId,
                            DirectoryEntryType type, String name, String specialty) {}
}
