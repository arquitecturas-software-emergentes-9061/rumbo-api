package com.uitopic.rumbo.analytics.domain.repositories;

import com.uitopic.rumbo.analytics.domain.model.aggregates.NavigationEvent;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationEventType;

import java.util.List;
import java.util.UUID;

/** Persistence contract of {@link NavigationEvent}, independent of the storage technology. Events are never updated. */
public interface NavigationEventRepository {
  /** Stores a batch of events in one round trip. */
  void saveAll(List<NavigationEvent> events);

  boolean existsBySessionIdAndType(UUID navigationSessionId, NavigationEventType eventType);
}
