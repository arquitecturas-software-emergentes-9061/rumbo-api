package com.uitopic.rumbo.analytics.infrastructure;

import com.uitopic.rumbo.TestcontainersConfiguration;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.DateRange;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.DestinationUsage;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationMetrics;
import com.uitopic.rumbo.analytics.infrastructure.persistence.jdbc.JdbcNavigationMetricsReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Site and period aggregations against real PostgreSQL, including repeated and malformed telemetry. */
@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@Transactional
class JdbcNavigationMetricsReaderIntegrationTest {
  private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
  private static final DateRange PERIOD = new DateRange(FROM, FROM.plusSeconds(3600));

  @Autowired JdbcNavigationMetricsReader reader;
  @Autowired JdbcTemplate jdbc;

  private final UUID siteId = UUID.randomUUID();

  @Test
  void consolidatesDistinctSessionsUsingTheLastFinishAndAnyArrivedResult() {
    UUID first = start("a", 0);
    event(siteId, first, "NAVIGATION_STARTED", 1, "{\"destinationNodeReference\":\"ignored\"}");
    event(siteId, first, "NAVIGATION_FINISHED", 10, "{\"result\":\"ARRIVED\",\"durationSeconds\":999}");
    event(siteId, first, "NAVIGATION_FINISHED", 20, "{\"result\":\"CANCELLED\",\"durationSeconds\":30}");
    UUID second = start("b", 2);
    event(siteId, second, "NAVIGATION_FINISHED", 20, "{\"result\":\"ARRIVED\",\"durationSeconds\":90.5}");
    UUID third = start("c", 3);
    start("d", 4);
    event(siteId, first, "DEVIATION_DETECTED", 30, "{}");
    event(siteId, first, "DEVIATION_DETECTED", 31, "{}");
    event(siteId, third, "DEVIATION_DETECTED", 32, "{}");
    event(siteId, first, "ROUTE_RECALCULATED", 33, "{}");
    event(siteId, first, "ROUTE_RECALCULATED", 34, "{}");
    event(siteId, third, "ROUTE_RECALCULATED", 35, "{}");

    NavigationMetrics metrics = reader.summarize(siteId, PERIOD);

    assertThat(metrics.startedSessions()).isEqualTo(4);
    assertThat(metrics.finishedSessions()).isEqualTo(2);
    assertThat(metrics.completionRate()).isCloseTo(0.5, within(0.000001));
    assertThat(metrics.averageDurationSeconds()).isCloseTo(60.25, within(0.000001));
    assertThat(metrics.averageDeviationsPerSession()).isCloseTo(0.75, within(0.000001));
    assertThat(metrics.recalculationRate()).isCloseTo(0.5, within(0.000001));
    assertThat(metrics.topDestinations()).containsExactly(new DestinationUsage("a", 1),
      new DestinationUsage("b", 1), new DestinationUsage("c", 1), new DestinationUsage("d", 1));
  }

  @Test
  void includesFromAndExcludesToOtherSitesAndSessionsStartedOutsideThePeriod() {
    UUID included = start("included", 0);
    UUID earlier = start("earlier", -1);
    start("at-to", 3600);
    event(siteId, earlier, "NAVIGATION_FINISHED", 10, "{\"result\":\"ARRIVED\",\"durationSeconds\":100}");
    event(siteId, earlier, "DEVIATION_DETECTED", 11, "{}");
    event(siteId, earlier, "ROUTE_RECALCULATED", 12, "{}");
    event(siteId, included, "NAVIGATION_FINISHED", 3600, "{\"result\":\"ARRIVED\",\"durationSeconds\":100}");
    event(siteId, included, "DEVIATION_DETECTED", 3601, "{}");
    event(siteId, included, "ROUTE_RECALCULATED", 3601, "{}");
    UUID otherSite = UUID.randomUUID();
    event(otherSite, UUID.randomUUID(), "NAVIGATION_STARTED", 1, "{}");
    event(otherSite, included, "NAVIGATION_FINISHED", 10, "{\"result\":\"ARRIVED\",\"durationSeconds\":100}");
    event(otherSite, included, "DEVIATION_DETECTED", 11, "{}");
    event(otherSite, included, "ROUTE_RECALCULATED", 12, "{}");

    assertThat(reader.summarize(siteId, PERIOD)).isEqualTo(new NavigationMetrics(1, 0, 0, 0, 0, 0,
      List.of(new DestinationUsage("included", 1))));
  }

  @Test
  void ignoresInvalidLastDurationsWithoutFallingBackToEarlierFinishes() {
    String[] durations = {"\"120\"", "-1", "null", "true", "{}", "[]"};
    for (String duration : durations) {
      UUID session = start("a", 0);
      event(siteId, session, "NAVIGATION_FINISHED", 10, "{\"durationSeconds\":100}");
      event(siteId, session, "NAVIGATION_FINISHED", 20, "{\"durationSeconds\":" + duration + "}");
    }
    UUID missing = start("a", 0);
    event(siteId, missing, "NAVIGATION_FINISHED", 20, "{}");

    NavigationMetrics invalid = reader.summarize(siteId, PERIOD);
    assertThat(invalid.finishedSessions()).isEqualTo(7);
    assertThat(invalid.averageDurationSeconds()).isZero();

    UUID zero = start("a", 0);
    event(siteId, zero, "NAVIGATION_FINISHED", 20, "{\"durationSeconds\":0}");
    UUID valid = start("a", 0);
    event(siteId, valid, "NAVIGATION_FINISHED", 20, "{\"durationSeconds\":80}");
    assertThat(reader.summarize(siteId, PERIOD).averageDurationSeconds()).isEqualTo(40);
  }

  @Test
  void ranksAtMostFiveDestinationsBySessionsThenReferenceUsingOnlyTheFirstStart() {
    for (String destination : new String[]{"f", "e", "d", "c", "b", "a"}) {
      start(destination, 0);
    }
    start("f", 1);
    UUID noDestination = UUID.randomUUID();
    event(siteId, noDestination, "NAVIGATION_STARTED", 0, "{}");
    event(siteId, noDestination, "NAVIGATION_STARTED", 1, "{\"destinationNodeReference\":\"a\"}");

    assertThat(reader.summarize(siteId, PERIOD).topDestinations()).containsExactly(
      new DestinationUsage("f", 2), new DestinationUsage("a", 1), new DestinationUsage("b", 1),
      new DestinationUsage("c", 1), new DestinationUsage("d", 1));
  }

  @Test
  void returnsEmptyWhenNoSessionStartedEvenIfOtherEventsExist() {
    assertThat(reader.summarize(siteId, PERIOD)).isSameAs(NavigationMetrics.EMPTY);
    UUID earlier = start("earlier", -1);
    event(siteId, earlier, "NAVIGATION_FINISHED", 10, "{\"result\":\"ARRIVED\"}");
    event(siteId, UUID.randomUUID(), "DEVIATION_DETECTED", 10, "{}");
    assertThat(reader.summarize(siteId, PERIOD)).isSameAs(NavigationMetrics.EMPTY);
    assertThat(reader.summarize(siteId, new DateRange(FROM, FROM))).isSameAs(NavigationMetrics.EMPTY);
  }

  private UUID start(String destination, long seconds) {
    UUID session = UUID.randomUUID();
    event(siteId, session, "NAVIGATION_STARTED", seconds,
      "{\"destinationNodeReference\":\"" + destination + "\"}");
    return session;
  }

  private void event(UUID site, UUID session, String type, long seconds, String metadata) {
    jdbc.update("""
      INSERT INTO navigation_events
        (id, navigation_session_id, site_id, event_type, occurred_at, received_at, metadata)
      VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
      """, UUID.randomUUID(), session, site, type, Timestamp.from(FROM.plusSeconds(seconds)),
      Timestamp.from(FROM.plusSeconds(seconds)), metadata);
  }
}
