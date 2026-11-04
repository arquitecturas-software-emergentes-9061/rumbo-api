package com.uitopic.rumbo.navigation.domain.model.valueobjects;
import java.util.UUID;
public record RouteNode(UUID floorId, UUID id, String label, double x, double y) {
  public RouteNode {
    if (floorId == null || id == null) throw new IllegalArgumentException("Route node ids required");
  }
  public NodeKey key() { return new NodeKey(floorId, id); }
}
