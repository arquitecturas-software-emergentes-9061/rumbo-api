package com.uitopic.rumbo.analytics.application.ports;

import com.uitopic.rumbo.analytics.domain.model.valueobjects.DateRange;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationMetrics;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port to a cache of calculated metrics. It is only an optimization: an adapter that cannot reach its store
 * answers as a cache miss and never fails the request, so the metrics are simply calculated again.
 */
public interface NavigationMetricsCache {
  Optional<NavigationMetrics> get(UUID siteId, DateRange period);

  void put(UUID siteId, DateRange period, NavigationMetrics metrics);

  /** Forgets every cached period of the sites, because new events changed their metrics. */
  void invalidate(Collection<UUID> siteIds);
}
