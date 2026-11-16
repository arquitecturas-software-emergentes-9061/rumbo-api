package com.uitopic.rumbo.navigation.domain.model.valueobjects;
import java.util.List;
public record RoutePath(List<RouteNode> nodes, List<RouteEdge> edges, double totalDistanceMeters) {
  public RoutePath { nodes = List.copyOf(nodes); edges = List.copyOf(edges); }
}
