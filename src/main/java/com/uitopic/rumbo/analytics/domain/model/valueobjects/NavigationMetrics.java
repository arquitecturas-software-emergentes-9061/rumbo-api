package com.uitopic.rumbo.analytics.domain.model.valueobjects;

import java.util.List;

/**
 * Indicators of a site in a period, calculated from {@code navigation_events} and never stored.
 * <ul>
 *   <li>{@code completionRate}: share of started sessions that finished with {@code result = ARRIVED}, from 0 to 1.</li>
 *   <li>{@code averageDurationSeconds}: mean {@code durationSeconds} of the finished sessions.</li>
 *   <li>{@code averageDeviationsPerSession}: DEVIATION_DETECTED events divided by started sessions.</li>
 *   <li>{@code recalculationRate}: share of started sessions with at least one ROUTE_RECALCULATED, from 0 to 1.</li>
 *   <li>{@code topDestinations}: destinations with most started sessions, most requested first.</li>
 * </ul>
 */
public record NavigationMetrics(long startedSessions, long finishedSessions, double completionRate,
                                double averageDurationSeconds, double averageDeviationsPerSession,
                                double recalculationRate, List<DestinationUsage> topDestinations) {
  public static final NavigationMetrics EMPTY = new NavigationMetrics(0, 0, 0, 0, 0, 0, List.of());

  public NavigationMetrics {
    if (startedSessions < 0 || finishedSessions < 0) throw new IllegalArgumentException("Counts cannot be negative");
    requireRate(completionRate, "completionRate");
    requireRate(recalculationRate, "recalculationRate");
    if (averageDurationSeconds < 0 || averageDeviationsPerSession < 0) {
      throw new IllegalArgumentException("Averages cannot be negative");
    }
    topDestinations = topDestinations == null ? List.of() : List.copyOf(topDestinations);
  }

  /** {@code false} when no session started in the period: the dashboard shows its empty state instead of zeros. */
  public boolean hasData() { return startedSessions > 0; }

  private static void requireRate(double rate, String name) {
    if (rate < 0 || rate > 1) throw new IllegalArgumentException(name + " must be between 0 and 1");
  }
}
