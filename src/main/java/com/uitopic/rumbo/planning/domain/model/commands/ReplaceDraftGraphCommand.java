package com.uitopic.rumbo.planning.domain.model.commands;

import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Complete replacement of the nodes and connections in one existing draft version. */
public record ReplaceDraftGraphCommand(UUID siteId, UUID mapId, UUID modelVersionId,
                                       List<NodeInput> nodes, List<ConnectionInput> connections) {
  public ReplaceDraftGraphCommand {
    Objects.requireNonNull(siteId, "siteId is required");
    Objects.requireNonNull(mapId, "mapId is required");
    Objects.requireNonNull(modelVersionId, "modelVersionId is required");
    nodes = List.copyOf(nodes);
    connections = List.copyOf(connections);
  }

  /** Node location in the stored floor-plan coordinate system. */
  public record NodeInput(UUID id, UUID floorId, UUID planVersionId,
                          double x, double y, NodeType type) {}

  /** Connection with separate physical distance and route cost. */
  public record ConnectionInput(UUID id, UUID sourceNodeId, UUID targetNodeId,
                                ConnectionType type, double distanceMeters, double weight,
                                boolean bidirectional) {}
}
