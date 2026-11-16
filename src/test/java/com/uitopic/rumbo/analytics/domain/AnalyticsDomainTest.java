package com.uitopic.rumbo.analytics.domain;

import com.uitopic.rumbo.analytics.domain.exceptions.InvalidDateRangeException;
import com.uitopic.rumbo.analytics.domain.model.aggregates.NavigationEvent;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.DateRange;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.EventMetadata;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationEventType;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationMetrics;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsDomainTest {
  private static final Instant NOW = Instant.parse("2026-11-04T15:00:00Z");

  @Test
  void anEventKeepsItsFactsAndNoUser() {
    NavigationEvent event = event(NOW.minusSeconds(30), new EventMetadata(Map.of("result", "ARRIVED",
      "durationSeconds", 240)));

    assertThat(event.isOfType(NavigationEventType.NAVIGATION_FINISHED)).isTrue();
    assertThat(event.metadata().get("durationSeconds")).contains(240);
    assertThat(event.receivedAt()).isEqualTo(NOW);
  }

  @Test
  void aDeviceClockSlightlyAheadIsTolerated() {
    assertThat(event(NOW.plus(Duration.ofMinutes(4)), EventMetadata.EMPTY).occurredAt()).isAfter(NOW);
    assertThatThrownBy(() -> event(NOW.plus(Duration.ofMinutes(6)), EventMetadata.EMPTY))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void metadataOnlyAcceptsKnownKeysWithSimpleValues() {
    assertThatThrownBy(() -> new EventMetadata(Map.of("email", "ana@clinica.pe")))
      .as("personal data cannot enter the analytics store").isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("email");
    assertThatThrownBy(() -> new EventMetadata(Map.of("result", Map.of("nested", true))))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EventMetadata(Map.of("reason", "x".repeat(101))))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aPeriodCannotStartAfterItEnds() {
    assertThatThrownBy(() -> new DateRange(NOW, NOW.minusSeconds(1))).isInstanceOf(InvalidDateRangeException.class);
    assertThatThrownBy(() -> new DateRange(NOW.minus(Duration.ofDays(400)), NOW))
      .isInstanceOf(InvalidDateRangeException.class);
  }

  @Test
  void aPeriodIncludesItsStartAndExcludesItsEnd() {
    DateRange range = new DateRange(NOW.minus(Duration.ofDays(7)), NOW);

    assertThat(range.contains(range.from())).isTrue();
    assertThat(range.contains(NOW)).isFalse();
  }

  @Test
  void metricsWithoutSessionsHaveNoData() {
    assertThat(NavigationMetrics.EMPTY.hasData()).isFalse();
    assertThatThrownBy(() -> new NavigationMetrics(1, 1, 1.5, 0, 0, 0, null))
      .isInstanceOf(IllegalArgumentException.class);
  }

  private static NavigationEvent event(Instant occurredAt, EventMetadata metadata) {
    return NavigationEvent.record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
      NavigationEventType.NAVIGATION_FINISHED, "node-12", occurredAt, metadata, NOW);
  }
}
