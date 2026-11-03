package com.uitopic.rumbo.planning.interfaces.rest.resources;

import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.DirectoryEntryType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapScale;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapVersionStatus;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Readable draft graph with the plan bounds needed by an editor. */
public record DraftGraphResource(UUID siteId, UUID mapId, UUID modelVersionId,
                                 MapVersionStatus status, List<PlanResource> plans,
                                 List<NodeResource> nodes, List<ConnectionResource> connections,
                                 List<DestinationResource> destinations) {
  /** Builds a REST snapshot without exposing the Planning aggregate. */
  public static DraftGraphResource from(IndoorMap map) {
    Map<UUID, MapScale> scales = map.scales().stream()
        .collect(Collectors.toMap(MapScale::planVersionId, Function.identity()));
    return new DraftGraphResource(map.siteId(), map.id(), map.modelVersionId(), map.status(),
        map.plans().stream().map(plan -> new PlanResource(plan.floorId(), plan.planVersionId(),
            plan.widthUnits(), plan.heightUnits(),
            scales.containsKey(plan.planVersionId())
                ? scales.get(plan.planVersionId()).metersPerUnit() : null)).toList(),
        map.nodes().stream().map(node -> new NodeResource(node.id(), node.position().floorId(),
            node.position().planVersionId(), node.position().x(), node.position().y(),
            node.type())).toList(),
        map.connections().stream().map(connection -> new ConnectionResource(connection.id(),
            connection.sourceNodeId(), connection.targetNodeId(), connection.type(),
            connection.distanceMeters(), connection.weight(), connection.verticalPenalty(),
            connection.bidirectional())).toList(),
        map.destinations().stream().map(destination -> new DestinationResource(destination.id(),
            destination.nodeId(), destination.type(), destination.name(),
            destination.specialty())).toList());
  }

  /** Floor-plan coordinate bounds and optional confirmed physical scale. */
  public record PlanResource(UUID floorId, UUID planVersionId, double widthUnits,
                             double heightUnits, Double metersPerUnit) {}

  /** Node identity and position in plan units. */
  public record NodeResource(UUID id, UUID floorId, UUID planVersionId, double x,
                             double y, NodeType type) {}

  /** Connection endpoints, type, distance in meters and independent route cost. */
  public record ConnectionResource(UUID id, UUID sourceNodeId, UUID targetNodeId,
                                   ConnectionType type, double distanceMeters,
                                   double weight, double verticalPenalty,
                                   boolean bidirectional) {}

  /** Editable medical entry and its destination node reference. */
  public record DestinationResource(UUID id, UUID nodeId, DirectoryEntryType type,
                                    String name, String specialty) {}
}
