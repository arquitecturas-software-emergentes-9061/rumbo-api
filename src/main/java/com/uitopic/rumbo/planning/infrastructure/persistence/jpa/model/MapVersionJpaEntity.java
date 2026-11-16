package com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model;

import com.uitopic.rumbo.planning.domain.model.valueobjects.MapVersionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
  @Enumerated(EnumType.STRING)
  private MapVersionStatus status;

  @Column(name = "edit_revision", nullable = false)
  private long editRevision;

  protected MapVersionJpaEntity() {}

  public MapVersionJpaEntity(UUID id, UUID mapId, MapVersionStatus status) {
    this(id, mapId, status, 0);
  }

  public MapVersionJpaEntity(UUID id, UUID mapId, MapVersionStatus status,
                             long editRevision) {
    this.id = id;
    this.mapId = mapId;
    this.status = status;
    this.editRevision = editRevision;
  }

  public UUID id() { return id; }
  public UUID mapId() { return mapId; }
  public MapVersionStatus status() { return status; }
  public long editRevision() { return editRevision; }

  /** Records a complete draft replacement under the version-row lock. */
  public void advanceEditRevision() { editRevision = Math.addExact(editRevision, 1); }

  /** Updates stored state after the domain has approved the transition. */
  public void changeStatus(MapVersionStatus status) { this.status = status; }
}
