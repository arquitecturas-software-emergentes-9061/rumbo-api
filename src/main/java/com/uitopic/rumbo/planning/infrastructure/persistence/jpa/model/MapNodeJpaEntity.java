package com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** Stored node of one draft model version. */
@Entity
@Table(name = "map_nodes")
public class MapNodeJpaEntity {
  @EmbeddedId
  private VersionedElementId id;

  @Column(name = "floor_id", nullable = false)
  private UUID floorId;

  @Column(name = "plan_version_id", nullable = false)
  private UUID planVersionId;

  @Column(name = "x", nullable = false)
  private double x;

  @Column(name = "y", nullable = false)
  private double y;

  @Column(name = "node_type", nullable = false, length = 32)
  private String nodeType;

  protected MapNodeJpaEntity() {}

  public MapNodeJpaEntity(VersionedElementId id, UUID floorId, UUID planVersionId,
                          double x, double y, String nodeType) {
    this.id = id;
    this.floorId = floorId;
    this.planVersionId = planVersionId;
    this.x = x;
    this.y = y;
    this.nodeType = nodeType;
  }

  public VersionedElementId id() { return id; }
  public UUID floorId() { return floorId; }
  public UUID planVersionId() { return planVersionId; }
  public double x() { return x; }
  public double y() { return y; }
  public String nodeType() { return nodeType; }
}
