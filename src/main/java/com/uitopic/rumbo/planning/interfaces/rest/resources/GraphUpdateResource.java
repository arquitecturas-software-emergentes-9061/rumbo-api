package com.uitopic.rumbo.planning.interfaces.rest.resources;

import com.uitopic.rumbo.planning.domain.model.commands.ReplaceDraftGraphCommand;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.DirectoryEntryType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Editor request replacing the graph and directory entries of one existing draft. */
public record GraphUpdateResource(
    @NotNull List<@NotNull @Valid NodeInputResource> nodes,
    @NotNull List<@NotNull @Valid ConnectionInputResource> connections,
    List<@NotNull @Valid DestinationInputResource> destinations) {

  public GraphUpdateResource {
    if (destinations == null) destinations = List.of();
  }

  /** Preserves requests that replace only nodes and connections. */
  public GraphUpdateResource(List<NodeInputResource> nodes,
                             List<ConnectionInputResource> connections) {
    this(nodes, connections, List.of());
  }

  /** Converts validated REST input to a Planning command. */
  public ReplaceDraftGraphCommand toCommand(UUID siteId, UUID mapId, UUID modelVersionId) {
    return new ReplaceDraftGraphCommand(siteId, mapId, modelVersionId,
        nodes.stream().map(node -> new ReplaceDraftGraphCommand.NodeInput(
            node.id(), node.floorId(), node.planVersionId(), node.x(), node.y(), node.type())).toList(),
        connections.stream().map(connection -> new ReplaceDraftGraphCommand.ConnectionInput(
            connection.id(), connection.sourceNodeId(), connection.targetNodeId(), connection.type(),
            connection.distanceMeters(), connection.weight(), connection.verticalPenalty(),
            connection.bidirectional())).toList(),
        destinations.stream().map(destination -> new ReplaceDraftGraphCommand.DestinationInput(
            destination.id(), destination.nodeId(), destination.type(),
            destination.name(), destination.specialty())).toList());
  }

  /** A node positioned in plan units, with its stable identifier. */
  public record NodeInputResource(@NotNull UUID id, @NotNull UUID floorId,
                                  @NotNull UUID planVersionId, @NotNull Double x,
                                  @NotNull Double y, @NotNull NodeType type) {}

  /** A weighted link between two node identifiers in the same map version. */
  public record ConnectionInputResource(@NotNull UUID id, @NotNull UUID sourceNodeId,
                                        @NotNull UUID targetNodeId, @NotNull ConnectionType type,
                                        @NotNull Double distanceMeters, @NotNull Double weight,
                                        Double verticalPenalty, @NotNull Boolean bidirectional) {
    public ConnectionInputResource {
      if (verticalPenalty == null) verticalPenalty = 0.0;
    }

    /** Preserves requests without an explicit vertical penalty. */
    public ConnectionInputResource(UUID id, UUID sourceNodeId, UUID targetNodeId,
                                   ConnectionType type, Double distanceMeters, Double weight,
                                   Boolean bidirectional) {
      this(id, sourceNodeId, targetNodeId, type, distanceMeters, weight, 0.0, bidirectional);
    }
  }

  /** Medical entry associated with a destination node of the replacement graph. */
  public record DestinationInputResource(@NotNull UUID id, @NotNull UUID nodeId,
                                         @NotNull DirectoryEntryType type,
                                         @NotNull String name, String specialty) {}
}
