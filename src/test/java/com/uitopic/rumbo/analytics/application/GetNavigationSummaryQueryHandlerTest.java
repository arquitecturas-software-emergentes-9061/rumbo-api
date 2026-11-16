package com.uitopic.rumbo.analytics.application;

import com.uitopic.rumbo.analytics.application.ports.NavigationMetricsCache;
import com.uitopic.rumbo.analytics.application.ports.NavigationMetricsReader;
import com.uitopic.rumbo.analytics.application.queryhandlers.GetNavigationSummaryQueryHandler;
import com.uitopic.rumbo.analytics.domain.exceptions.MetricsUnavailableException;
import com.uitopic.rumbo.analytics.domain.model.queries.GetNavigationSummaryQuery;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.DateRange;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetNavigationSummaryQueryHandlerTest {
  private static final UUID SITE = UUID.randomUUID();
  private static final DateRange WEEK = new DateRange(Instant.parse("2026-11-01T00:00:00Z"),
    Instant.parse("2026-11-08T00:00:00Z"));
  private static final GetNavigationSummaryQuery QUERY = new GetNavigationSummaryQuery(SITE, WEEK);
  private static final NavigationMetrics METRICS = new NavigationMetrics(10, 8, 0.8, 180, 0.5, 0.2, List.of());

  private final NavigationMetricsCache cache = mock(NavigationMetricsCache.class);
  private final NavigationMetricsReader reader = mock(NavigationMetricsReader.class);

  @Test
  void aCachedSummaryIsNotCalculatedAgain() {
    when(cache.get(SITE, WEEK)).thenReturn(Optional.of(METRICS));

    assertThat(handler(readers(reader)).handle(QUERY)).isEqualTo(METRICS);
    verify(reader, never()).summarize(SITE, WEEK);
  }

  @Test
  void aMissIsCalculatedAndCached() {
    when(cache.get(SITE, WEEK)).thenReturn(Optional.empty());
    when(reader.summarize(SITE, WEEK)).thenReturn(METRICS);

    assertThat(handler(readers(reader)).handle(QUERY)).isEqualTo(METRICS);
    verify(cache).put(SITE, WEEK, METRICS);
  }

  @Test
  void withoutAReaderTheSummaryIsUnavailable() {
    when(cache.get(SITE, WEEK)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler(readers()).handle(QUERY)).isInstanceOf(MetricsUnavailableException.class);
  }

  private GetNavigationSummaryQueryHandler handler(ObjectProvider<NavigationMetricsReader> readers) {
    return new GetNavigationSummaryQueryHandler(readers, cache);
  }

  private static ObjectProvider<NavigationMetricsReader> readers(NavigationMetricsReader... available) {
    StaticListableBeanFactory factory = new StaticListableBeanFactory();
    for (NavigationMetricsReader reader : available) factory.addBean("reader", reader);
    return factory.getBeanProvider(NavigationMetricsReader.class);
  }
}
