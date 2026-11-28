package com.uitopic.rumbo.analytics.domain;

import com.uitopic.rumbo.analytics.domain.exceptions.InvalidDateRangeException;
import com.uitopic.rumbo.analytics.domain.model.aggregates.FeedbackFact;
import com.uitopic.rumbo.analytics.domain.model.events.NavigationIncidentReportedEvent;
import com.uitopic.rumbo.analytics.domain.model.events.NavigationRatedEvent;
import com.uitopic.rumbo.analytics.domain.model.queries.FeedbackQueries.ListIncidentReportsQuery;
import com.uitopic.rumbo.analytics.domain.model.queries.InsightQueries.GetIncidentZonesQuery;
import com.uitopic.rumbo.analytics.domain.model.queries.InsightQueries.GetNavigationTrendQuery;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.DateRange;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.FeedbackKind;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.IncidentCategory;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.TrendGranularity;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Rules of the feedback contract and of the dashboard queries (WI-TS31-05, WI-US25-09). */
class NavigationFeedbackDomainTest {
  private static final Instant NOW = Instant.parse("2026-11-03T15:00:00Z");
  private static final UUID SITE = UUID.randomUUID();

  @Test
  void aRatingNeedsAScoreFromOneToFive() {
    assertThatThrownBy(() -> new NavigationRatedEvent(UUID.randomUUID(), UUID.randomUUID(), SITE, null, 0, NOW))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new NavigationRatedEvent(UUID.randomUUID(), UUID.randomUUID(), SITE, null, 6, NOW))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new NavigationRatedEvent(UUID.randomUUID(), null, SITE, null, 3, NOW))
      .isInstanceOf(NullPointerException.class);
  }

  @Test
  void aReportMayComeWithoutPositionButAPositionNeedsBothCoordinatesAndItsFloor() {
    var withoutPosition = new NavigationIncidentReportedEvent(UUID.randomUUID(), null, SITE, null, "  ",
      IncidentCategory.OTHER, null, null, NOW);

    assertThat(withoutPosition.hasPosition()).isFalse();
    assertThat(withoutPosition.nodeReference()).isNull();
    assertThatThrownBy(() -> new NavigationIncidentReportedEvent(UUID.randomUUID(), null, SITE, UUID.randomUUID(),
      null, IncidentCategory.OTHER, 1.0, null, NOW)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new NavigationIncidentReportedEvent(UUID.randomUUID(), null, SITE, null, null,
      IncidentCategory.OTHER, 1.0, 2.0, NOW)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new NavigationIncidentReportedEvent(UUID.randomUUID(), null, SITE, UUID.randomUUID(),
      null, IncidentCategory.OTHER, Double.NaN, 2.0, NOW)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aFactKeepsTheIdOfItsSourceSoARepetitionIsTheSameFact() {
    var event = new NavigationIncidentReportedEvent(UUID.randomUUID(), UUID.randomUUID(), SITE, UUID.randomUUID(),
      "pasillo-a", IncidentCategory.BLOCKED_PATH, 1.0, 2.0, NOW);

    FeedbackFact fact = FeedbackFact.of(event);

    assertThat(fact).isEqualTo(FeedbackFact.of(event));
    assertThat(fact.sourceId()).isEqualTo(event.reportId());
    assertThat(fact.kind()).isEqualTo(FeedbackKind.INCIDENT);
    assertThat(fact.score()).isNull();
  }

  @Test
  void aTrendCoversAtMostTheDaysOrWeeksOfItsGranularity() {
    Instant from = Instant.parse("2026-08-01T05:00:00Z");
    DateRange ninetyTwoDays = new DateRange(from, from.plus(Duration.ofDays(92)));
    DateRange ninetyThreeDays = new DateRange(from, from.plus(Duration.ofDays(93)));

    TrendGranularity.DAY.requireCovers(ninetyTwoDays);
    assertThatThrownBy(() -> TrendGranularity.DAY.requireCovers(ninetyThreeDays))
      .isInstanceOf(InvalidDateRangeException.class);
    TrendGranularity.WEEK.requireCovers(ninetyThreeDays);
  }

  @Test
  void queriesRejectInvalidPagesCellsAndTimeZones() {
    DateRange day = new DateRange(NOW, NOW.plusSeconds(86_400));

    assertThatThrownBy(() -> new ListIncidentReportsQuery(SITE, day, null, -1, 20))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ListIncidentReportsQuery(SITE, day, null, 0, 101))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GetIncidentZonesQuery(SITE, day, null, 0))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GetIncidentZonesQuery(SITE, day, null, Double.NaN))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> GetNavigationTrendQuery.zone("Mars/Olympus"))
      .isInstanceOf(IllegalArgumentException.class);
    assertThat(GetNavigationTrendQuery.zone(null)).isEqualTo(ZoneId.of("America/Lima"));
  }
}
