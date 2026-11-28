package com.uitopic.rumbo.analytics.domain.model.valueobjects;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An incident report as the dashboard lists it. Position and node are {@code null} when the visitor did not send them.
 */
public record IncidentRecord(UUID reportId, UUID navigationSessionId, UUID floorId, String nodeReference,
                             IncidentCategory category, Double x, Double y, Instant reportedAt) {

  /** One page of incident reports, newest first. */
  public record Page(List<IncidentRecord> items, long total, int page, int size) {
    public Page {
      items = List.copyOf(items);
    }
  }
}
