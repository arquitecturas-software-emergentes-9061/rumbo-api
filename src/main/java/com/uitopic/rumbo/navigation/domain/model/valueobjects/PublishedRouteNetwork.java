package com.uitopic.rumbo.navigation.domain.model.valueobjects;
import java.util.List;
import java.util.UUID;
/** Read-side snapshot supplied by the Design/Planning bounded context. */
public record PublishedRouteNetwork(UUID siteId, List<RouteNode> nodes,
                                    List<RouteEdge> edges, List<WallSegment> walls) {
  public PublishedRouteNetwork {
    if (siteId == null) throw new IllegalArgumentException("Site required");
    nodes = List.copyOf(nodes); edges = List.copyOf(edges); walls = List.copyOf(walls);
  }
}
