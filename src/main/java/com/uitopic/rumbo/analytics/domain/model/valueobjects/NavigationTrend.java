package com.uitopic.rumbo.analytics.domain.model.valueobjects;

import java.time.Instant;
import java.util.List;

/**
 * Indicators of a site over time (WI-US25-07). Every point of the period is present, with zeros when nothing
 * happened, so a chart never skips a day.
 *
 * @param zone time zone that delimits the days or weeks
 */
public record NavigationTrend(TrendGranularity granularity, String zone, List<Point> points) {
  public NavigationTrend {
    points = List.copyOf(points);
  }

  public boolean hasData() {
    return points.stream().anyMatch(point -> point.startedSessions() > 0 || point.incidents() > 0
      || point.ratings() > 0);
  }

  /**
   * One day or week.
   *
   * @param periodStart first instant of the day or week in {@code zone}
   * @param averageScore mean rating of the point, or {@code null} without ratings
   */
  public record Point(Instant periodStart, long startedSessions, long finishedSessions, long arrivedSessions,
                      long deviations, long recalculations, long incidents, long ratings, Double averageScore) { }
}
