package com.uitopic.rumbo.analytics.domain.model.valueobjects;

import java.util.Objects;

/**
 * Demand of one destination in a period.
 *
 * @param destinationNodeReference node of the destination in the published map
 * @param sessionCount sessions that started towards it
 */
public record DestinationUsage(String destinationNodeReference, long sessionCount) {
  public DestinationUsage {
    Objects.requireNonNull(destinationNodeReference, "destinationNodeReference is required");
    if (sessionCount < 0) throw new IllegalArgumentException("sessionCount cannot be negative");
  }
}
