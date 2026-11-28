package com.uitopic.rumbo.analytics.application;

import com.uitopic.rumbo.analytics.application.commandhandlers.RecordFeedbackFactCommandHandler;
import com.uitopic.rumbo.analytics.application.eventhandlers.NavigationFeedbackEventHandler;
import com.uitopic.rumbo.analytics.application.ports.AdministeredSites;
import com.uitopic.rumbo.analytics.application.ports.NavigationInsightsReader;
import com.uitopic.rumbo.analytics.application.queryhandlers.NavigationFeedbackQueryHandler;
import com.uitopic.rumbo.analytics.application.queryhandlers.NavigationInsightsQueryHandler;
import com.uitopic.rumbo.analytics.domain.exceptions.MetricsUnavailableException;
import com.uitopic.rumbo.analytics.domain.exceptions.SessionFeedbackNotFoundException;
import com.uitopic.rumbo.analytics.domain.model.aggregates.FeedbackFact;
import com.uitopic.rumbo.analytics.domain.model.events.NavigationRatedEvent;
import com.uitopic.rumbo.analytics.domain.model.queries.FeedbackQueries.GetSessionFeedbackQuery;
import com.uitopic.rumbo.analytics.domain.model.queries.InsightQueries.GetSitesOverviewQuery;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.DateRange;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.SiteActivity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Application rules of TS-31 and US-25 with test doubles for the read model and Planning. */
class NavigationFeedbackHandlersTest {
  private static final Instant NOW = Instant.parse("2026-11-03T15:00:00Z");
  private static final DateRange DAY = new DateRange(NOW, NOW.plusSeconds(86_400));

  private final NavigationInsightsReader reader = mock(NavigationInsightsReader.class);

  @Test
  void aFailureRecordingTheFactNeverReachesTheVisitorFeedback() {
    RecordFeedbackFactCommandHandler commands = mock(RecordFeedbackFactCommandHandler.class);
    when(commands.handle(any())).thenThrow(new IllegalStateException("database down"));
    NavigationFeedbackEventHandler handler = new NavigationFeedbackEventHandler(commands);

    assertThatCode(() -> handler.on(new NavigationRatedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
      null, 4, NOW))).doesNotThrowAnyException();
  }

  @Test
  void aRepeatedEventIsRecordedAsTheSameFact() {
    RecordFeedbackFactCommandHandler commands = mock(RecordFeedbackFactCommandHandler.class);
    NavigationFeedbackEventHandler handler = new NavigationFeedbackEventHandler(commands);
    var event = new NavigationRatedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 4, NOW);

    handler.on(event);
    handler.on(event);

    verify(commands, org.mockito.Mockito.times(2)).handle(FeedbackFact.of(event));
  }

  @Test
  void aSessionWithoutFeedbackOnTheSiteIsNotFound() {
    when(reader.sessionFeedback(any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> new NavigationFeedbackQueryHandler(reader)
      .handle(new GetSessionFeedbackQuery(UUID.randomUUID(), UUID.randomUUID())))
      .isInstanceOf(SessionFeedbackNotFoundException.class);
  }

  @Test
  void theOverviewKeepsThePlanningOrderAndFillsSitesWithoutActivity() {
    UUID admin = UUID.randomUUID();
    UUID busy = UUID.randomUUID();
    UUID quiet = UUID.randomUUID();
    AdministeredSites sites = user -> List.of(new AdministeredSites.Site(quiet, "Sede A"),
      new AdministeredSites.Site(busy, "Sede B"));
    when(reader.activity(List.of(quiet, busy), DAY))
      .thenReturn(List.of(new SiteActivity(busy, null, 10, 8, 0.7, 2, 3, 4.5)));

    List<SiteActivity> overview = handler(sites).handle(new GetSitesOverviewQuery(admin, DAY));

    assertThat(overview).containsExactly(SiteActivity.empty(quiet, "Sede A"),
      new SiteActivity(busy, "Sede B", 10, 8, 0.7, 2, 3, 4.5));
  }

  @Test
  void anAdministratorWithoutSitesGetsAnEmptyOverviewWithoutReadingMetrics() {
    List<SiteActivity> overview = handler(user -> List.of())
      .handle(new GetSitesOverviewQuery(UUID.randomUUID(), DAY));

    assertThat(overview).isEmpty();
    verify(reader, never()).activity(any(), any());
  }

  @Test
  void withoutPlanningTheOverviewIsUnavailable() {
    assertThatThrownBy(() -> handler(null).handle(new GetSitesOverviewQuery(UUID.randomUUID(), DAY)))
      .isInstanceOf(MetricsUnavailableException.class);
  }

  @SuppressWarnings("unchecked")
  private NavigationInsightsQueryHandler handler(AdministeredSites sites) {
    ObjectProvider<AdministeredSites> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(sites);
    return new NavigationInsightsQueryHandler(reader, provider);
  }
}
