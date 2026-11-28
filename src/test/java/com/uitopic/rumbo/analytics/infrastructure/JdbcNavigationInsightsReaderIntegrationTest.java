package com.uitopic.rumbo.analytics.infrastructure;

import com.uitopic.rumbo.TestcontainersConfiguration;
import com.uitopic.rumbo.analytics.domain.model.aggregates.FeedbackFact;
import com.uitopic.rumbo.analytics.domain.model.events.NavigationIncidentReportedEvent;
import com.uitopic.rumbo.analytics.domain.model.events.NavigationRatedEvent;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.DateRange;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.FeedbackSummary;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.FeedbackSummary.CategoryCount;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.FeedbackSummary.ScoreCount;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.IncidentCategory;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.IncidentRecord;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.IncidentZones;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationTrend;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.SessionFeedback;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.SiteActivity;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.TrendGranularity;
import com.uitopic.rumbo.analytics.infrastructure.persistence.jdbc.JdbcFeedbackFactRepository;
import com.uitopic.rumbo.analytics.infrastructure.persistence.jdbc.JdbcNavigationInsightsReader;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Feedback facts and dashboard aggregations against real PostgreSQL (WI-TS31-07, WI-US25-09): repeated events,
 * reports without position, period limits, empty sites and separation between sites.
 */
@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@Transactional
class JdbcNavigationInsightsReaderIntegrationTest {
  private static final Instant FROM = Instant.parse("2026-11-01T05:00:00Z"); // 1 Nov, 00:00 in Lima
  private static final DateRange DAY = new DateRange(FROM, FROM.plusSeconds(86_400));
  private static final DateRange WEEK = new DateRange(FROM, FROM.plusSeconds(7 * 86_400));
  private static final ZoneId LIMA = ZoneId.of("America/Lima");

  @Autowired JdbcFeedbackFactRepository facts;
  @Autowired JdbcNavigationInsightsReader reader;
  @Autowired JdbcTemplate jdbc;

  private final UUID siteId = UUID.randomUUID();
  private final UUID floorId = UUID.randomUUID();

  @Nested
  class FeedbackFacts {
    @Test
    void aRepeatedEventIsStoredOnce() {
      NavigationRatedEvent rating = rating(UUID.randomUUID(), 4, 60);

      assertThat(facts.recordOnce(FeedbackFact.of(rating))).isTrue();
      assertThat(facts.recordOnce(FeedbackFact.of(rating))).isFalse();

      assertThat(count()).isEqualTo(1);
    }

    @Test
    void aSecondRatingOfTheSameSessionIsIgnored() {
      UUID session = UUID.randomUUID();

      assertThat(facts.recordOnce(FeedbackFact.of(rating(session, 5, 60)))).isTrue();
      assertThat(facts.recordOnce(FeedbackFact.of(rating(session, 1, 120)))).isFalse();

      assertThat(reader.sessionFeedback(siteId, session)).get()
        .satisfies(feedback -> assertThat(feedback.rating().score()).isEqualTo(5));
    }

    @Test
    void anIncidentWithoutPositionOrSessionIsKeptAndListed() {
      facts.recordOnce(FeedbackFact.of(incident(null, null, null, IncidentCategory.SIGNAL_LOST, null, 30)));

      IncidentRecord.Page page = reader.incidents(siteId, DAY, null, 0, 20);

      assertThat(page.total()).isEqualTo(1);
      assertThat(page.items().get(0)).satisfies(item -> {
        assertThat(item.navigationSessionId()).isNull();
        assertThat(item.x()).isNull();
        assertThat(item.category()).isEqualTo(IncidentCategory.SIGNAL_LOST);
      });
    }
  }

  @Nested
  class Summary {
    @Test
    void consolidatesRatingsAndReportsOfThePeriodOnly() {
      record(rating(UUID.randomUUID(), 5, 10));
      record(rating(UUID.randomUUID(), 4, 20));
      record(rating(UUID.randomUUID(), 4, 30));
      record(incident(UUID.randomUUID(), "pasillo-a", 1.0, IncidentCategory.BLOCKED_PATH, floorId, 40));
      record(incident(UUID.randomUUID(), "pasillo-a", null, IncidentCategory.BLOCKED_PATH, floorId, 50));
      record(incident(null, null, null, IncidentCategory.MAP_MISMATCH, null, 60));
      record(rating(UUID.randomUUID(), 1, -1)); // before the period
      record(rating(UUID.randomUUID(), 1, 86_400)); // at its end, excluded
      facts.recordOnce(FeedbackFact.of(new NavigationRatedEvent(UUID.randomUUID(), UUID.randomUUID(),
        UUID.randomUUID(), null, 1, FROM.plusSeconds(10)))); // another site

      FeedbackSummary summary = reader.feedbackSummary(siteId, DAY);

      assertThat(summary.ratings()).isEqualTo(3);
      assertThat(summary.averageScore()).isCloseTo(13.0 / 3, within(0.0001));
      assertThat(summary.scoreDistribution()).containsExactly(new ScoreCount(1, 0), new ScoreCount(2, 0),
        new ScoreCount(3, 0), new ScoreCount(4, 2), new ScoreCount(5, 1));
      assertThat(summary.incidents()).isEqualTo(3);
      assertThat(summary.incidentsWithoutPosition()).isEqualTo(2);
      assertThat(summary.incidentsByCategory()).containsExactly(
        new CategoryCount(IncidentCategory.BLOCKED_PATH, 2), new CategoryCount(IncidentCategory.MAP_MISMATCH, 1));
    }

    @Test
    void aSiteWithoutFeedbackHasNoData() {
      FeedbackSummary summary = reader.feedbackSummary(siteId, DAY);

      assertThat(summary.hasData()).isFalse();
      assertThat(summary.averageScore()).isNull();
      assertThat(summary.scoreDistribution()).hasSize(5).allMatch(score -> score.ratings() == 0);
    }

    @Test
    void reportsAreFilteredByCategoryAndPagedNewestFirst() {
      for (int second = 1; second <= 5; second++) {
        record(incident(null, null, null, IncidentCategory.WRONG_DIRECTIONS, null, second));
      }
      record(incident(null, null, null, IncidentCategory.OTHER, null, 10));

      IncidentRecord.Page second = reader.incidents(siteId, DAY, IncidentCategory.WRONG_DIRECTIONS, 1, 2);

      assertThat(second.total()).isEqualTo(5);
      assertThat(second.items()).extracting(IncidentRecord::reportedAt)
        .containsExactly(FROM.plusSeconds(3), FROM.plusSeconds(2));
    }

    @Test
    void theFeedbackOfASessionIsOnlyReadOnItsSite() {
      UUID session = UUID.randomUUID();
      record(rating(session, 3, 10));
      record(incident(session, "sala-espera", null, IncidentCategory.OTHER, floorId, 5));

      SessionFeedback feedback = reader.sessionFeedback(siteId, session).orElseThrow();

      assertThat(feedback.rating().score()).isEqualTo(3);
      assertThat(feedback.incidents()).hasSize(1);
      assertThat(reader.sessionFeedback(UUID.randomUUID(), session)).isEmpty();
      assertThat(reader.sessionFeedback(siteId, UUID.randomUUID())).isEmpty();
    }
  }

  @Nested
  class Trends {
    @Test
    void everyDayOfThePeriodIsPresentAndDaysFollowTheLimaClock() {
      UUID first = UUID.randomUUID();
      event(first, "NAVIGATION_STARTED", 60, "{}");
      event(first, "DEVIATION_DETECTED", 120, "{}");
      event(first, "NAVIGATION_FINISHED", 300, "{\"result\":\"ARRIVED\",\"durationSeconds\":240}");
      UUID lateNight = UUID.randomUUID();
      event(lateNight, "NAVIGATION_STARTED", 86_399, "{}"); // 1 Nov at 23:59:59 in Lima, 2 Nov in UTC
      record(rating(first, 5, 400));
      record(incident(null, null, null, IncidentCategory.OTHER, null, 2 * 86_400 + 10));

      NavigationTrend trend = reader.trend(siteId, WEEK, TrendGranularity.DAY, LIMA);

      assertThat(trend.points()).hasSize(7);
      assertThat(trend.points().get(0)).satisfies(day -> {
        assertThat(day.periodStart()).isEqualTo(FROM);
        assertThat(day.startedSessions()).isEqualTo(2);
        assertThat(day.arrivedSessions()).isEqualTo(1);
        assertThat(day.deviations()).isEqualTo(1);
        assertThat(day.averageScore()).isEqualTo(5.0);
      });
      assertThat(trend.points().get(1).startedSessions()).isZero();
      assertThat(trend.points().get(1).averageScore()).isNull();
      assertThat(trend.points().get(2).incidents()).isEqualTo(1);
    }

    @Test
    void aWeeklyTrendStartsOnMonday() {
      NavigationTrend trend = reader.trend(siteId, new DateRange(FROM, FROM.plusSeconds(14 * 86_400)),
        TrendGranularity.WEEK, LIMA);

      // 1 Nov 2026 is a Sunday: its week started on Monday 26 Oct.
      assertThat(trend.points()).extracting(NavigationTrend.Point::periodStart).containsExactly(
        Instant.parse("2026-10-26T05:00:00Z"), Instant.parse("2026-11-02T05:00:00Z"),
        Instant.parse("2026-11-09T05:00:00Z"));
      assertThat(trend.hasData()).isFalse();
    }
  }

  @Nested
  class Zones {
    @Test
    void joinsDeviationsAndReportsByNodeAndGroupsPositionsInCells() {
      UUID session = UUID.randomUUID();
      event(session, "DEVIATION_DETECTED", 10, "{}", floorId, "pasillo-a");
      event(session, "DEVIATION_DETECTED", 11, "{}", floorId, "pasillo-a");
      event(session, "ROUTE_RECALCULATED", 12, "{}", floorId, "pasillo-a");
      event(session, "DEVIATION_DETECTED", 13, "{}", floorId, "escalera");
      record(incident(session, "pasillo-a", 0.5, IncidentCategory.BLOCKED_PATH, floorId, 20));
      record(incident(session, "pasillo-a", 1.5, IncidentCategory.BLOCKED_PATH, floorId, 21));
      record(incident(null, "ascensor", 9.0, IncidentCategory.ACCESSIBILITY_BARRIER, floorId, 22));
      record(incident(null, null, null, IncidentCategory.OTHER, null, 23));

      IncidentZones zones = reader.incidentZones(siteId, DAY, null, 2.0);

      assertThat(zones.zones()).containsExactly(
        new IncidentZones.Zone(floorId, "pasillo-a", 2, 1, 2, IncidentCategory.BLOCKED_PATH),
        new IncidentZones.Zone(floorId, "ascensor", 0, 0, 1, IncidentCategory.ACCESSIBILITY_BARRIER),
        new IncidentZones.Zone(floorId, "escalera", 1, 0, 0, null));
      assertThat(zones.hotspots()).containsExactly(new IncidentZones.Hotspot(floorId, 1.0, 1.0, 2),
        new IncidentZones.Hotspot(floorId, 9.0, 9.0, 1));
      assertThat(zones.incidentsWithoutLocation()).isEqualTo(1);
    }

    @Test
    void aFloorFilterLeavesOtherFloorsOut() {
      UUID otherFloor = UUID.randomUUID();
      event(UUID.randomUUID(), "DEVIATION_DETECTED", 10, "{}", otherFloor, "hall");
      record(incident(null, "hall", 3.0, IncidentCategory.OTHER, otherFloor, 20));

      IncidentZones zones = reader.incidentZones(siteId, DAY, floorId, 2.0);

      assertThat(zones.hasData()).isFalse();
    }
  }

  @Test
  void activityIsReadPerSiteAndASiteWithoutActivityIsMissing() {
    UUID quietSite = UUID.randomUUID();
    UUID session = UUID.randomUUID();
    event(session, "NAVIGATION_STARTED", 10, "{}");
    event(session, "NAVIGATION_FINISHED", 20, "{\"result\":\"ARRIVED\"}");
    event(UUID.randomUUID(), "NAVIGATION_STARTED", 30, "{}");
    record(rating(session, 4, 40));

    List<SiteActivity> activity = reader.activity(List.of(siteId, quietSite), DAY);

    assertThat(activity).singleElement().satisfies(site -> {
      assertThat(site.siteId()).isEqualTo(siteId);
      assertThat(site.startedSessions()).isEqualTo(2);
      assertThat(site.completionRate()).isCloseTo(0.5, within(0.0001));
      assertThat(site.averageScore()).isEqualTo(4.0);
    });
    assertThat(reader.activity(List.of(), DAY)).isEmpty();
  }

  private NavigationRatedEvent rating(UUID session, int score, int second) {
    return new NavigationRatedEvent(UUID.randomUUID(), session, siteId, "cardiologia-201", score,
      FROM.plusSeconds(second));
  }

  private NavigationIncidentReportedEvent incident(UUID session, String node, Double coordinate,
                                                   IncidentCategory category, UUID floor, int second) {
    return new NavigationIncidentReportedEvent(UUID.randomUUID(), session, siteId, floor, node, category, coordinate,
      coordinate, FROM.plusSeconds(second));
  }

  private void record(NavigationRatedEvent event) { facts.recordOnce(FeedbackFact.of(event)); }

  private void record(NavigationIncidentReportedEvent event) { facts.recordOnce(FeedbackFact.of(event)); }

  private void event(UUID session, String type, int second, String metadata) {
    event(session, type, second, metadata, null, null);
  }

  private void event(UUID session, String type, int second, String metadata, UUID floor, String node) {
    Instant at = FROM.plusSeconds(second);
    jdbc.update("""
      INSERT INTO navigation_events (id, navigation_session_id, site_id, floor_id, event_type, node_reference,
        occurred_at, received_at, metadata) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))""",
      UUID.randomUUID(), session, siteId, floor, type, node, Timestamp.from(at), Timestamp.from(at), metadata);
  }

  private long count() {
    Long total = jdbc.queryForObject("SELECT count(*) FROM analytics_feedback_facts WHERE site_id = ?", Long.class,
      siteId);
    return total == null ? 0 : total;
  }
}
