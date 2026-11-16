package com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Persistence row for the IndoorMap aggregate identity and site ownership. */
@Entity
@Table(name = "indoor_maps")
public class IndoorMapJpaEntity {
  @Id
  private UUID id;

  @Column(name = "site_id", nullable = false)
  private UUID siteId;

  protected IndoorMapJpaEntity() {}

  public IndoorMapJpaEntity(UUID id, UUID siteId) {
    this.id = id;
    this.siteId = siteId;
  }

  public UUID id() { return id; }
  public UUID siteId() { return siteId; }
}
