package com.uitopic.rumbo.analytics.infrastructure.persistence.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationEventType;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Row of {@code navigation_events}. It implements {@link Persistable} because the identifier is assigned by the
 * domain: without it, Spring Data would run a SELECT per event before every insert to decide whether it is new.
 */
@Entity
@Table(name = "navigation_events")
public class NavigationEventJpaEntity implements Persistable<UUID> {
  @Id
  private UUID id;

  @Column(name = "navigation_session_id", nullable = false, updatable = false)
  private UUID navigationSessionId;

  @Column(name = "site_id", nullable = false, updatable = false)
  private UUID siteId;

  @Column(name = "floor_id", updatable = false)
  private UUID floorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, updatable = false, length = 30)
  private NavigationEventType eventType;

  @Column(name = "node_reference", updatable = false, length = 100)
  private String nodeReference;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  @Column(name = "received_at", nullable = false, updatable = false)
  private Instant receivedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", nullable = false, updatable = false, columnDefinition = "jsonb")
  private Map<String, Object> metadata;

  @Transient
  private boolean isNew = true;

  @Override
  public UUID getId() { return id; }

  @Override
  public boolean isNew() { return isNew; }

  @PostLoad
  @PostPersist
  void markPersisted() { isNew = false; }

  public void setId(UUID id) { this.id = id; }
  public UUID getNavigationSessionId() { return navigationSessionId; }
  public void setNavigationSessionId(UUID navigationSessionId) { this.navigationSessionId = navigationSessionId; }
  public UUID getSiteId() { return siteId; }
  public void setSiteId(UUID siteId) { this.siteId = siteId; }
  public UUID getFloorId() { return floorId; }
  public void setFloorId(UUID floorId) { this.floorId = floorId; }
  public NavigationEventType getEventType() { return eventType; }
  public void setEventType(NavigationEventType eventType) { this.eventType = eventType; }
  public String getNodeReference() { return nodeReference; }
  public void setNodeReference(String nodeReference) { this.nodeReference = nodeReference; }
  public Instant getOccurredAt() { return occurredAt; }
  public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
  public Instant getReceivedAt() { return receivedAt; }
  public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
  public Map<String, Object> getMetadata() { return metadata; }
  public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
