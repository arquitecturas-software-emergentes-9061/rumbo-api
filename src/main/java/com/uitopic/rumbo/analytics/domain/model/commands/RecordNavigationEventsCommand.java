package com.uitopic.rumbo.analytics.domain.model.commands;

import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationEventType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Batch of navigation facts sent by the mobile app (TS-30). The batch is stored whole or not at all.
 *
 * @param events facts in the order the device produced them
 */
public record RecordNavigationEventsCommand(List<Event> events) {
  public RecordNavigationEventsCommand {
    events = events == null ? List.of() : List.copyOf(events);
  }

  /** One fact of a navigation session, as reported by the device. */
  public record Event(UUID navigationSessionId, UUID siteId, UUID floorId, NavigationEventType eventType,
                      String nodeReference, Instant occurredAt, Map<String, Object> metadata) {
  }
}
