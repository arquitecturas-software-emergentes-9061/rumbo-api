package com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** Stored medical directory entry scoped to one indoor-map version. */
@Entity
@Table(name = "map_destinations")
public class MapDestinationJpaEntity {
  @EmbeddedId
  private VersionedElementId id;

  @Column(name = "node_id", nullable = false)
  private UUID nodeId;

  @Column(name = "entry_type", nullable = false, length = 16)
  private String entryType;

  @Column(name = "name", nullable = false, length = 150)
  private String name;

  @Column(name = "specialty", length = 150)
  private String specialty;

  @Column(name = "search_text", nullable = false)
  private String searchText;

  protected MapDestinationJpaEntity() {}

  public MapDestinationJpaEntity(VersionedElementId id, UUID nodeId, String entryType,
                                 String name, String specialty, String searchText) {
    this.id = id;
    this.nodeId = nodeId;
    this.entryType = entryType;
    this.name = name;
    this.specialty = specialty;
    this.searchText = searchText;
  }

  public VersionedElementId id() { return id; }
  public UUID nodeId() { return nodeId; }
  public String entryType() { return entryType; }
  public String name() { return name; }
  public String specialty() { return specialty; }
}
