package com.uitopic.rumbo.analytics.infrastructure.persistence.jpa.mappers;

import com.uitopic.rumbo.analytics.domain.model.aggregates.NavigationEvent;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.EventMetadata;
import com.uitopic.rumbo.analytics.infrastructure.persistence.jpa.entities.NavigationEventJpaEntity;

/** Converts {@link NavigationEvent} to and from its row. */
public final class NavigationEventPersistenceMapper {
  private NavigationEventPersistenceMapper() {}

  public static NavigationEventJpaEntity toEntity(NavigationEvent event) {
    NavigationEventJpaEntity entity = new NavigationEventJpaEntity();
    entity.setId(event.id());
    entity.setNavigationSessionId(event.navigationSessionId());
    entity.setSiteId(event.siteId());
    entity.setFloorId(event.floorId().orElse(null));
    entity.setEventType(event.eventType());
    entity.setNodeReference(event.nodeReference().orElse(null));
    entity.setOccurredAt(event.occurredAt());
    entity.setReceivedAt(event.receivedAt());
    entity.setMetadata(event.metadata().values());
    return entity;
  }

  public static NavigationEvent toDomain(NavigationEventJpaEntity entity) {
    return NavigationEvent.restore(entity.getId(), entity.getNavigationSessionId(), entity.getSiteId(),
      entity.getFloorId(), entity.getEventType(), entity.getNodeReference(), entity.getOccurredAt(),
      entity.getReceivedAt(), new EventMetadata(entity.getMetadata()));
  }
}
