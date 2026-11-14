package com.uitopic.rumbo.analytics.application.ports;

import com.uitopic.rumbo.analytics.domain.model.valueobjects.DateRange;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationHeatmap;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationMetrics;

import java.util.UUID;

/**
 * Read side of Analytics (CQRS): consolidated indicators calculated by aggregation over {@code navigation_events},
 * without loading aggregates into memory. Implemented in Infrastructure by {@code JdbcNavigationMetricsReader}
 * (WI-TS30-03); heatmaps and route statistics will be added here as further operations.
 * <p>Contract for the implementation, so the summary endpoint works unchanged when it arrives:
 * <ul>
 *   <li>Only events of {@code siteId} with {@code period.from() <= occurred_at < period.to()} count.</li>
 *   <li>A session counts as started when it has a NAVIGATION_STARTED in the period; the indicators follow the
 *   definitions documented in {@link NavigationMetrics}, read from the metadata keys of {@code EventMetadata}.</li>
 *   <li>A period without started sessions returns {@link NavigationMetrics#EMPTY}, never {@code null}.</li>
 *   <li>{@code topDestinations} has at most 5 entries.</li>
 *   <li>Caching is not its job: the query handler already caches the results.</li>
 * </ul>
 */
public interface NavigationMetricsReader {
  int TOP_DESTINATIONS = 5;

  int HEATMAP_MAX_CELLS = 500;

  NavigationMetrics summarize(UUID siteId, DateRange period);

  /** Counts deviations and recalculations by floor and node in the site's half-open period.
   * Returns at most 500 cells ranked by deviations, recalculations, floor and node.
   * Events missing either location field are counted separately.
   */
  NavigationHeatmap heatmap(UUID siteId, DateRange period);
}
