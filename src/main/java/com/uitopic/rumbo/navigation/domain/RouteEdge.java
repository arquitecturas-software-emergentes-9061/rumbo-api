package com.uitopic.rumbo.navigation.domain;
public record RouteEdge(NodeKey from, NodeKey to, double distanceMeters, String kind,
                        boolean accessible, boolean bidirectional) {
  public RouteEdge {
    if (from == null || to == null) throw new IllegalArgumentException("Edge nodes required");
    if (!(distanceMeters > 0) || !Double.isFinite(distanceMeters))
      throw new IllegalArgumentException("Distance must be finite and positive");
    if (kind == null || kind.isBlank()) throw new IllegalArgumentException("Edge kind required");
  }
  public RouteEdge reversed() { return new RouteEdge(to, from, distanceMeters, kind, accessible, false); }
}
