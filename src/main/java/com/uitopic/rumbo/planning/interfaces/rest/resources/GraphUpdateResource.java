package com.uitopic.rumbo.planning.interfaces.rest.resources;

import com.uitopic.rumbo.planning.domain.model.commands.ReplaceDraftGraphCommand;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionAccessibility;
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
    @NotNull List<@NotNull @Valid DestinationInputResource> destinations) {

  /** Converts validated REST input to a Planning command. */
  public ReplaceDraftGraphCommand toCommand(UUID siteId, UUID mapId, UUID modelVersionId,
                                            long expectedRevision) {
    return new ReplaceDraftGraphCommand(siteId, mapId, modelVersionId, expectedRevision,
        nodes.stream().map(node -> new ReplaceDraftGraphCommand.NodeInput(
            node.id(), node.floorId(), node.planVersionId(), node.x(), node.y(), node.type())).toList(),
        connections.stream().map(connection -> new ReplaceDraftGraphCommand.ConnectionInput(
            connection.id(), connection.sourceNodeId(), connection.targetNodeId(), connection.type(),
            connection.distanceMeters(), connection.weight(), connection.verticalPenalty(),
            connection.accessibility(), connection.bidirectional())).toList(),
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
                                        Double verticalPenalty, ConnectionAccessibility accessibility,
                                        @NotNull Boolean bidirectional) {
    public ConnectionInputResource {
      if (verticalPenalty == null) verticalPenalty = 0.0;
      if (accessibility == null) accessibility = ConnectionAccessibility.UNKNOWN;
    }

    /** Preserves callers without an explicit accessibility classification. */
    public ConnectionInputResource(UUID id, UUID sourceNodeId, UUID targetNodeId,
                                   ConnectionType type, Double distanceMeters, Double weight,
                                   Double verticalPenalty, Boolean bidirectional) {
      this(id, sourceNodeId, targetNodeId, type, distanceMeters, weight, verticalPenalty,
          ConnectionAccessibility.UNKNOWN, bidirectional);
    }

    /** Preserves requests without an explicit vertical penalty. */
    public ConnectionInputResource(UUID id, UUID sourceNodeId, UUID targetNodeId,
                                   ConnectionType type, Double distanceMeters, Double weight,
                                   Boolean bidirectional) {
      this(id, sourceNodeId, targetNodeId, type, distanceMeters, weight, 0.0,
          ConnectionAccessibility.UNKNOWN, bidirectional);
    }
  }

  /** Medical entry associated with a destination node of the replacement graph. */
  public record DestinationInputResource(@NotNull UUID id, @NotNull UUID nodeId,
                                         @NotNull DirectoryEntryType type,
                                         @NotNull String name, String specialty) {}
}
