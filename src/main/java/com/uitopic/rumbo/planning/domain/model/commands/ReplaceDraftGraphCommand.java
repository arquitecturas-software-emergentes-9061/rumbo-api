package com.uitopic.rumbo.planning.domain.model.commands;

import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.DirectoryEntryType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Complete replacement of the nodes and connections in one existing draft version. */
public record ReplaceDraftGraphCommand(UUID siteId, UUID mapId, UUID modelVersionId,
                                       List<NodeInput> nodes, List<ConnectionInput> connections,
                                       List<DestinationInput> destinations) {
  public ReplaceDraftGraphCommand {
    Objects.requireNonNull(siteId, "siteId is required");
    Objects.requireNonNull(mapId, "mapId is required");
    Objects.requireNonNull(modelVersionId, "modelVersionId is required");
    nodes = List.copyOf(nodes);
    connections = List.copyOf(connections);
    destinations = List.copyOf(destinations);
  }

  /** Preserves callers that replace only the spatial graph. */
  public ReplaceDraftGraphCommand(UUID siteId, UUID mapId, UUID modelVersionId,
                                  List<NodeInput> nodes, List<ConnectionInput> connections) {
    this(siteId, mapId, modelVersionId, nodes, connections, List.of());
  }

  /** Node location in the stored floor-plan coordinate system. */
  public record NodeInput(UUID id, UUID floorId, UUID planVersionId,
                          double x, double y, NodeType type) {}

  /** Connection with separate physical distance and route cost. */
  public record ConnectionInput(UUID id, UUID sourceNodeId, UUID targetNodeId,
                                ConnectionType type, double distanceMeters, double weight,
                                double verticalPenalty, boolean bidirectional) {
    /** Preserves callers without an explicit penalty. */
    public ConnectionInput(UUID id, UUID sourceNodeId, UUID targetNodeId,
                           ConnectionType type, double distanceMeters, double weight,
                           boolean bidirectional) {
      this(id, sourceNodeId, targetNodeId, type, distanceMeters, weight, 0, bidirectional);
    }
  }

  /** Medical directory metadata tied to a destination node of this version. */
  public record DestinationInput(UUID id, UUID nodeId, DirectoryEntryType type,
                                 String name, String specialty) {}
}
