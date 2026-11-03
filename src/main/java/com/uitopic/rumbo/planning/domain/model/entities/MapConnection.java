package com.uitopic.rumbo.planning.domain.model.entities;

import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import java.util.Objects;
import java.util.UUID;

/**
 * A weighted directed or bidirectional link between node IDs of one map version.
 * Physical distance in meters is distinct from routing weight in cost units.
 * The IndoorMap aggregate checks endpoint existence and cross-floor compatibility.
 * This entity enforces scalar invariants.
 */
public final class MapConnection {
  private final UUID id;
  private final UUID modelVersionId;
  private final UUID sourceNodeId;
  private final UUID targetNodeId;
  private final ConnectionType type;
  private final double distanceMeters;
  private final double weight;
  private final boolean bidirectional;

  /** Creates one connection with separate physical distance and routing weight. */
  public MapConnection(UUID id, UUID modelVersionId, UUID sourceNodeId, UUID targetNodeId,
                       ConnectionType type, double distanceMeters, double weight,
                       boolean bidirectional) {
    this.id = Objects.requireNonNull(id, "id is required");
    this.modelVersionId = Objects.requireNonNull(modelVersionId, "modelVersionId is required");
    this.sourceNodeId = Objects.requireNonNull(sourceNodeId, "sourceNodeId is required");
    this.targetNodeId = Objects.requireNonNull(targetNodeId, "targetNodeId is required");
    this.type = Objects.requireNonNull(type, "type is required");
    if (sourceNodeId.equals(targetNodeId)) {
      throw new IllegalArgumentException("Connection endpoints must differ");
    }
    if (!Double.isFinite(distanceMeters) || distanceMeters <= 0
        || !Double.isFinite(weight) || weight <= 0) {
      throw new IllegalArgumentException("Distance and weight must be finite and positive");
    }
    this.distanceMeters = distanceMeters;
    this.weight = weight;
    this.bidirectional = bidirectional;
  }

  public UUID id() { return id; }
  public UUID modelVersionId() { return modelVersionId; }
  public UUID sourceNodeId() { return sourceNodeId; }
  public UUID targetNodeId() { return targetNodeId; }
  public ConnectionType type() { return type; }
  public double distanceMeters() { return distanceMeters; }
  public double weight() { return weight; }
  public boolean bidirectional() { return bidirectional; }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof MapConnection connection)) return false;
    return modelVersionId.equals(connection.modelVersionId) && id.equals(connection.id);
  }

  @Override
  public int hashCode() { return Objects.hash(modelVersionId, id); }
}
