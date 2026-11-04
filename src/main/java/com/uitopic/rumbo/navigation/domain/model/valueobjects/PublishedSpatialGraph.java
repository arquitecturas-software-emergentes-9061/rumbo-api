package com.uitopic.rumbo.navigation.domain.model.valueobjects;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Navigation's versioned read model for published spatial geometry and route costs. */
public record PublishedSpatialGraph(UUID siteId, UUID mapId, UUID modelVersionId,
                                    List<Plan> plans, List<Node> nodes, List<Link> links) {
  public PublishedSpatialGraph {
    Objects.requireNonNull(siteId, "siteId is required");
    Objects.requireNonNull(mapId, "mapId is required");
    Objects.requireNonNull(modelVersionId, "modelVersionId is required");
    plans = List.copyOf(plans);
    nodes = List.copyOf(nodes);
    links = List.copyOf(links);
  }

  /** Floor-plan dimensions and conversion from visual units to meters. */
  public record Plan(UUID floorId, UUID planVersionId, double widthUnits,
                     double heightUnits, double metersPerUnit) {}

  /** Navigable point in the coordinate frame of one plan version. */
  public record Node(UUID id, UUID floorId, UUID planVersionId,
                     double x, double y, NodeRole role) {}

  /** Persisted connection with separate distance and cost. */
  public record Link(UUID id, UUID sourceNodeId, UUID targetNodeId,
                     LinkKind kind, double distanceMeters, double weight,
                     double verticalPenalty, Accessibility accessibility,
                     boolean bidirectional) {
    /** Preserves unclassified published links. */
    public Link(UUID id, UUID sourceNodeId, UUID targetNodeId,
                LinkKind kind, double distanceMeters, double weight,
                double verticalPenalty, boolean bidirectional) {
      this(id, sourceNodeId, targetNodeId, kind, distanceMeters, weight,
          verticalPenalty, Accessibility.UNKNOWN, bidirectional);
    }
  }

  /** Mobility classification translated from the Planning published contract. */
  public enum Accessibility { UNKNOWN, ACCESSIBLE, INACCESSIBLE }

  /** Semantic role used by Navigation without importing Planning entities. */
  public enum NodeRole { INTERSECTION, ACCESS, DESTINATION, VERTICAL_LINK, REFERENCE }

  /** Traversal kind used by Navigation without importing Planning entities. */
  public enum LinkKind { CORRIDOR, DOORWAY, STAIRS, RAMP, ELEVATOR }
}
