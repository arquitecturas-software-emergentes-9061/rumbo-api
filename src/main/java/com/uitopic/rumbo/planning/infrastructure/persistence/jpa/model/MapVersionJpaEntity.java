package com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Persistence row that reserves version status for the publication use case. */
@Entity
@Table(name = "map_versions")
public class MapVersionJpaEntity {
  @Id
  private UUID id;

  @Column(name = "map_id", nullable = false)
  private UUID mapId;

  @Column(name = "status", nullable = false, length = 16)
  private String status;

  protected MapVersionJpaEntity() {}

  public MapVersionJpaEntity(UUID id, UUID mapId, String status) {
    this.id = id;
    this.mapId = mapId;
    this.status = status;
  }

  public UUID id() { return id; }
  public UUID mapId() { return mapId; }
  public String status() { return status; }
}
