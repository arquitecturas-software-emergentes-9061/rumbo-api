package com.uitopic.rumbo.planning.interfaces.rest.resources;

import com.uitopic.rumbo.planning.domain.model.commands.ReplaceDraftGraphCommand;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Editor request replacing all nodes and connections of one existing draft. */
public record GraphUpdateResource(
    @NotNull List<@NotNull @Valid NodeInputResource> nodes,
    @NotNull List<@NotNull @Valid ConnectionInputResource> connections) {

  /** Converts validated REST input to a Planning command. */
  public ReplaceDraftGraphCommand toCommand(UUID siteId, UUID mapId, UUID modelVersionId) {
    return new ReplaceDraftGraphCommand(siteId, mapId, modelVersionId,
        nodes.stream().map(node -> new ReplaceDraftGraphCommand.NodeInput(
            node.id(), node.floorId(), node.planVersionId(), node.x(), node.y(), node.type())).toList(),
        connections.stream().map(connection -> new ReplaceDraftGraphCommand.ConnectionInput(
            connection.id(), connection.sourceNodeId(), connection.targetNodeId(), connection.type(),
            connection.distanceMeters(), connection.weight(), connection.bidirectional())).toList());
  }

  /** A node positioned in plan units, with its stable identifier. */
  public record NodeInputResource(@NotNull UUID id, @NotNull UUID floorId,
                                  @NotNull UUID planVersionId, @NotNull Double x,
                                  @NotNull Double y, @NotNull NodeType type) {}

  /** A weighted link between two node identifiers in the same map version. */
  public record ConnectionInputResource(@NotNull UUID id, @NotNull UUID sourceNodeId,
                                        @NotNull UUID targetNodeId, @NotNull ConnectionType type,
                                        @NotNull Double distanceMeters, @NotNull Double weight,
                                        @NotNull Boolean bidirectional) {}
}
