package com.uitopic.rumbo.navigation.domain.model.valueobjects;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Ordered route over one published map, with physical distance and cost kept separate. */
public record CalculatedRoute(UUID siteId, UUID modelVersionId,
                              List<PublishedSpatialGraph.Node> nodes, List<Leg> legs,
                              double totalDistanceMeters, double totalCost) {
  public CalculatedRoute {
    Objects.requireNonNull(siteId, "siteId is required");
    Objects.requireNonNull(modelVersionId, "modelVersionId is required");
    nodes = List.copyOf(nodes);
    legs = List.copyOf(legs);
    if (nodes.size() != legs.size() + 1) {
      throw new IllegalArgumentException("A route must have one more node than traversals");
    }
    for (int index = 0; index < legs.size(); index++) {
      if (!nodes.get(index).id().equals(legs.get(index).fromNodeId())
          || !nodes.get(index + 1).id().equals(legs.get(index).toNodeId())) {
        throw new IllegalArgumentException("Route traversals must follow the ordered nodes");
      }
    }
    if (!Double.isFinite(totalDistanceMeters) || totalDistanceMeters < 0
        || !Double.isFinite(totalCost) || totalCost < 0) {
      throw new IllegalArgumentException("Route distance and cost must be finite and nonnegative");
    }
  }

  /** One traversal in route order, including its actual direction and applied penalty. */
  public record Leg(UUID linkId, UUID fromNodeId, UUID toNodeId,
                    PublishedSpatialGraph.LinkKind kind, double distanceMeters,
                    double weight, double verticalPenalty,
                    PublishedSpatialGraph.Accessibility accessibility) {
    /** Preserves older route legs without an explicit mobility classification. */
    public Leg(UUID linkId, UUID fromNodeId, UUID toNodeId,
               PublishedSpatialGraph.LinkKind kind, double distanceMeters,
               double weight, double verticalPenalty) {
      this(linkId, fromNodeId, toNodeId, kind, distanceMeters, weight,
          verticalPenalty, PublishedSpatialGraph.Accessibility.UNKNOWN);
    }
    /** Cost of traversing this link in the published model. */
    public double cost() { return weight + verticalPenalty; }
  }
}
