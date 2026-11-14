package com.uitopic.rumbo.analytics.application.queryhandlers;

import com.uitopic.rumbo.analytics.application.ports.NavigationMetricsReader;
import com.uitopic.rumbo.analytics.domain.exceptions.MetricsUnavailableException;
import com.uitopic.rumbo.analytics.domain.model.queries.GetNavigationHeatmapQuery;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationHeatmap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class GetNavigationHeatmapQueryHandler {
  private final ObjectProvider<NavigationMetricsReader> readers;

  public GetNavigationHeatmapQueryHandler(ObjectProvider<NavigationMetricsReader> readers) {
    this.readers = readers;
  }

  /** The caller must check site ownership before calling this query. */
  public NavigationHeatmap handle(GetNavigationHeatmapQuery query) {
    NavigationMetricsReader reader = readers.getIfAvailable();
    if (reader == null) throw new MetricsUnavailableException("Navigation metrics are not available yet");
    return reader.heatmap(query.siteId(), query.period());
  }
}
