package com.uitopic.rumbo.navigation.interfaces.rest.resources;

import com.uitopic.rumbo.navigation.domain.model.valueobjects.NodeKey;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.RoutePath;
import java.util.List;
import java.util.UUID;

/** Response of the provisional demo route endpoint. */
public record RouteResponse(double totalDistanceMeters, List<RouteNodeResponse> nodes,
                            List<RouteEdgeResponse> links) {
  public static RouteResponse from(RoutePath path) {
    return new RouteResponse(path.totalDistanceMeters(),
        path.nodes().stream().map(node -> new RouteNodeResponse(node.floorId(), node.id(),
            node.label(), node.x(), node.y())).toList(),
        path.edges().stream().map(edge -> new RouteEdgeResponse(edge.from(), edge.to(),
            edge.distanceMeters(), edge.kind(), edge.accessible())).toList());
  }

  /** Node in traversal order. */
  public record RouteNodeResponse(UUID floorId, UUID nodeId, String label,
                                  double x, double y) {}

  /** Directed traversal in route order. */
  public record RouteEdgeResponse(NodeKey from, NodeKey to, double distanceMeters,
                                  String kind, boolean accessible) {}
}
