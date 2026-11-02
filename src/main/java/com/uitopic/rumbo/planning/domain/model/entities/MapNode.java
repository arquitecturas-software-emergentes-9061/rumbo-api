package com.uitopic.rumbo.planning.domain.model.entities;

import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PlanCoordinateSpace;
import com.uitopic.rumbo.planning.domain.model.valueobjects.SpatialCoordinate;
import java.util.Objects;
import java.util.UUID;

/**
 * A navigable point belonging to an indoor-map version in the Planning context.
 * The coordinate retains the floor and plan version used to place the point on its
 * visual plan. Identity is the pair of model version and node ID, not its position.
 * The IndoorMap aggregate will govern edits and publication in a later iteration.
 */
public final class MapNode {
  private final UUID id;
  private final UUID modelVersionId;
  private final SpatialCoordinate position;
  private final NodeType type;

  /** Creates a node placed within the coordinate space of one floor-plan version. */
  public MapNode(UUID id, UUID modelVersionId, SpatialCoordinate position,
                 NodeType type, PlanCoordinateSpace space) {
    this.id = Objects.requireNonNull(id, "id is required");
    this.modelVersionId = Objects.requireNonNull(modelVersionId, "modelVersionId is required");
    this.position = Objects.requireNonNull(position, "position is required");
    this.type = Objects.requireNonNull(type, "type is required");
    if (space == null || !space.contains(position)) {
      throw new IllegalArgumentException("Node position must belong to the floor-plan version and its bounds");
    }
  }

  public UUID id() { return id; }
  public UUID modelVersionId() { return modelVersionId; }
  public SpatialCoordinate position() { return position; }
  public NodeType type() { return type; }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof MapNode node)) return false;
    return modelVersionId.equals(node.modelVersionId) && id.equals(node.id);
  }

  @Override
  public int hashCode() { return Objects.hash(modelVersionId, id); }
}
