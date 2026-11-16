package com.uitopic.rumbo.analytics.application.queryhandlers;

import com.uitopic.rumbo.analytics.application.ports.NavigationMetricsCache;
import com.uitopic.rumbo.analytics.application.ports.NavigationMetricsReader;
import com.uitopic.rumbo.analytics.domain.exceptions.MetricsUnavailableException;
import com.uitopic.rumbo.analytics.domain.model.queries.GetNavigationSummaryQuery;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Answers the dashboard summary (TS-30 scenario 2) from the cache when possible and from the read model otherwise.
 * The reader is looked up lazily: until an implementation of {@link NavigationMetricsReader} is registered, the
 * endpoint answers 503 instead of keeping the whole application from starting.
 */
@Service
public class GetNavigationSummaryQueryHandler {
  private final ObjectProvider<NavigationMetricsReader> readers;
  private final NavigationMetricsCache cache;

  public GetNavigationSummaryQueryHandler(ObjectProvider<NavigationMetricsReader> readers,
                                          NavigationMetricsCache cache) {
    this.readers = readers;
    this.cache = cache;
  }

  public NavigationMetrics handle(GetNavigationSummaryQuery query) {
    return cache.get(query.siteId(), query.period()).orElseGet(() -> {
      NavigationMetricsReader reader = readers.getIfAvailable();
      if (reader == null) throw new MetricsUnavailableException("Navigation metrics are not available yet");
      NavigationMetrics metrics = reader.summarize(query.siteId(), query.period());
      cache.put(query.siteId(), query.period(), metrics);
      return metrics;
    });
  }
}
