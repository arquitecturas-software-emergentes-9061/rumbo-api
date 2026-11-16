package com.uitopic.rumbo.navigation.interfaces.rest.resources;

import com.uitopic.rumbo.navigation.domain.model.valueobjects.CalculatedRoute;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedSpatialGraph;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Versioned route with ordered nodes and traversals. */
public record PublishedRouteResponse(UUID siteId, UUID modelVersionId,
                                     double totalDistanceMeters, double totalCost,
                                     List<NodeResource> nodes, List<LinkResource> links,
                                     List<VerticalSectionResource> verticalSections) {
  /** Projects the calculated route without changing Navigation's domain model. */
  public static PublishedRouteResponse from(CalculatedRoute route) {
    List<VerticalSectionResource> verticalSections = new ArrayList<>();
    for (int index = 0; index < route.legs().size(); index++) {
      var from = route.nodes().get(index);
      var to = route.nodes().get(index + 1);
      if (!from.floorId().equals(to.floorId())) {
        var leg = route.legs().get(index);
        verticalSections.add(new VerticalSectionResource(index, leg.linkId(),
            leg.fromNodeId(), leg.toNodeId(), from.floorId(), to.floorId(),
            leg.kind(), leg.distanceMeters(), leg.cost()));
      }
    }
    return new PublishedRouteResponse(route.siteId(), route.modelVersionId(),
        route.totalDistanceMeters(), route.totalCost(),
        route.nodes().stream().map(node -> new NodeResource(node.id(), node.floorId(),
            node.planVersionId(), node.x(), node.y(), node.role())).toList(),
        route.legs().stream().map(leg -> new LinkResource(leg.linkId(),
            leg.fromNodeId(), leg.toNodeId(), leg.kind(), leg.distanceMeters(),
            leg.weight(), leg.verticalPenalty(), leg.accessibility())).toList(),
        List.copyOf(verticalSections));
  }

  /** Route node in the coordinate frame of its floor plan. */
  public record NodeResource(UUID nodeId, UUID floorId, UUID planVersionId,
                             double x, double y, PublishedSpatialGraph.NodeRole role) {}

  /** Traversal in route order and direction, with physical distance and cost fields. */
  public record LinkResource(UUID linkId, UUID fromNodeId, UUID toNodeId,
                             PublishedSpatialGraph.LinkKind type, double distanceMeters,
                             double weight, double verticalPenalty,
                             PublishedSpatialGraph.Accessibility accessibility) {}

  /** Cross-floor traversal in route order; linkIndex points into the ordered links. */
  public record VerticalSectionResource(int linkIndex, UUID linkId, UUID fromNodeId,
                                        UUID toNodeId, UUID fromFloorId, UUID toFloorId,
                                        PublishedSpatialGraph.LinkKind type,
                                        double distanceMeters, double cost) {}
}
