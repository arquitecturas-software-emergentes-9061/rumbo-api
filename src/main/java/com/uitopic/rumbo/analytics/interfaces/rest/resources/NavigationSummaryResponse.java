package com.uitopic.rumbo.analytics.interfaces.rest.resources;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Dashboard summary of a site in a period. With {@code hasData = false} the indicators are zero and the web shows
 * its empty state.
 */
public record NavigationSummaryResponse(UUID siteId, Instant from, Instant to, boolean hasData, long startedSessions,
                                        long finishedSessions, double completionRate, double averageDurationSeconds,
                                        double averageDeviationsPerSession, double recalculationRate,
                                        List<DestinationUsageResponse> topDestinations) {
  public record DestinationUsageResponse(String destinationNodeReference, long sessionCount) {
  }
}
