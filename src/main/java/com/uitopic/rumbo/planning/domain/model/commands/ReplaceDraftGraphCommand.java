package com.uitopic.rumbo.planning.domain.model.commands;

import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionAccessibility;
import com.uitopic.rumbo.planning.domain.model.valueobjects.DirectoryEntryType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapWall;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapDoor;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Complete replacement of editable graph elements in one existing draft version. */
public record ReplaceDraftGraphCommand(UUID siteId, UUID mapId, UUID modelVersionId,
                                       long expectedRevision,
                                       List<NodeInput> nodes, List<ConnectionInput> connections,
                                       List<DestinationInput> destinations,
                                       List<MapWall> walls, List<MapDoor> doors) {
  public ReplaceDraftGraphCommand(UUID siteId, UUID mapId, UUID modelVersionId,
                                  long expectedRevision, List<NodeInput> nodes,
                                  List<ConnectionInput> connections, List<DestinationInput> destinations) {
    this(siteId, mapId, modelVersionId, expectedRevision, nodes, connections, destinations, List.of(), List.of());
  }
  public ReplaceDraftGraphCommand {
    Objects.requireNonNull(siteId, "siteId is required");
    Objects.requireNonNull(mapId, "mapId is required");
    Objects.requireNonNull(modelVersionId, "modelVersionId is required");
    if (expectedRevision < 0) {
      throw new IllegalArgumentException("expectedRevision must be nonnegative");
    }
    nodes = List.copyOf(nodes);
    connections = List.copyOf(connections);
    destinations = List.copyOf(destinations);
    if (walls != null) walls = List.copyOf(walls);
    if (doors != null) doors = List.copyOf(doors);
  }

  /** Node location in the stored floor-plan coordinate system. */
  public record NodeInput(UUID id, UUID floorId, UUID planVersionId,
                          double x, double y, NodeType type) {}

  /** Connection with separate physical distance and route cost. */
  public record ConnectionInput(UUID id, UUID sourceNodeId, UUID targetNodeId,
                                ConnectionType type, double distanceMeters, double weight,
                                double verticalPenalty, ConnectionAccessibility accessibility,
                                boolean bidirectional) {
    public ConnectionInput {
      Objects.requireNonNull(accessibility, "accessibility is required");
    }

    /** Preserves callers that have not classified mobility yet. */
    public ConnectionInput(UUID id, UUID sourceNodeId, UUID targetNodeId,
                           ConnectionType type, double distanceMeters, double weight,
                           double verticalPenalty, boolean bidirectional) {
      this(id, sourceNodeId, targetNodeId, type, distanceMeters, weight, verticalPenalty,
          ConnectionAccessibility.UNKNOWN, bidirectional);
    }
    /** Preserves callers without an explicit penalty. */
    public ConnectionInput(UUID id, UUID sourceNodeId, UUID targetNodeId,
                           ConnectionType type, double distanceMeters, double weight,
                           boolean bidirectional) {
      this(id, sourceNodeId, targetNodeId, type, distanceMeters, weight, 0,
          ConnectionAccessibility.UNKNOWN, bidirectional);
    }
  }

  /** Medical directory metadata tied to a destination node of this version. */
  public record DestinationInput(UUID id, UUID nodeId, DirectoryEntryType type,
                                 String name, String specialty) {}
}
