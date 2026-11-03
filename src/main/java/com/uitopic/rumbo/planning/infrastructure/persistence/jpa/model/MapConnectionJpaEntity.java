package com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** Stored weighted connection of one draft model version. */
@Entity
@Table(name = "map_connections")
public class MapConnectionJpaEntity {
  @EmbeddedId
  private VersionedElementId id;

  @Column(name = "source_node_id", nullable = false)
  private UUID sourceNodeId;

  @Column(name = "target_node_id", nullable = false)
  private UUID targetNodeId;

  @Column(name = "connection_type", nullable = false, length = 32)
  private String connectionType;

  @Column(name = "distance_meters", nullable = false)
  private double distanceMeters;

  @Column(name = "weight", nullable = false)
  private double weight;

  @Column(name = "vertical_penalty", nullable = false)
  private double verticalPenalty;

  @Column(name = "bidirectional", nullable = false)
  private boolean bidirectional;

  protected MapConnectionJpaEntity() {}

  public MapConnectionJpaEntity(VersionedElementId id, UUID sourceNodeId, UUID targetNodeId,
                                String connectionType, double distanceMeters, double weight,
                                double verticalPenalty, boolean bidirectional) {
    this.id = id;
    this.sourceNodeId = sourceNodeId;
    this.targetNodeId = targetNodeId;
    this.connectionType = connectionType;
    this.distanceMeters = distanceMeters;
    this.weight = weight;
    this.verticalPenalty = verticalPenalty;
    this.bidirectional = bidirectional;
  }

  public VersionedElementId id() { return id; }
  public UUID sourceNodeId() { return sourceNodeId; }
  public UUID targetNodeId() { return targetNodeId; }
  public String connectionType() { return connectionType; }
  public double distanceMeters() { return distanceMeters; }
  public double weight() { return weight; }
  public double verticalPenalty() { return verticalPenalty; }
  public boolean bidirectional() { return bidirectional; }
}
