package com.uitopic.rumbo.analytics.interfaces.rest;

import com.uitopic.rumbo.analytics.application.commandhandlers.RecordNavigationEventsCommandHandler;
import com.uitopic.rumbo.analytics.application.queryhandlers.GetNavigationSummaryQueryHandler;
import com.uitopic.rumbo.analytics.domain.model.commands.RecordNavigationEventsCommand;
import com.uitopic.rumbo.analytics.domain.model.queries.GetNavigationSummaryQuery;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.DateRange;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationMetrics;
import com.uitopic.rumbo.analytics.interfaces.rest.resources.NavigationSummaryResponse;
import com.uitopic.rumbo.analytics.interfaces.rest.resources.NavigationSummaryResponse.DestinationUsageResponse;
import com.uitopic.rumbo.analytics.interfaces.rest.resources.RecordNavigationEventsRequest;
import com.uitopic.rumbo.shared.interfaces.rest.openapi.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/analytics", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Analytics", description = "Navigation telemetry and dashboard metrics")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public class AnalyticsController {
  private final RecordNavigationEventsCommandHandler recordEvents;
  private final GetNavigationSummaryQueryHandler getSummary;

  public AnalyticsController(RecordNavigationEventsCommandHandler recordEvents,
                             GetNavigationSummaryQueryHandler getSummary) {
    this.recordEvents = recordEvents;
    this.getSummary = getSummary;
  }

  @PostMapping(value = "/navigation-events", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(summary = "Record anonymized navigation events from the device (TS-30); the app does not wait for "
    + "metrics to be recalculated")
  public void recordNavigationEvents(@Valid @RequestBody RecordNavigationEventsRequest request) {
    recordEvents.handle(new RecordNavigationEventsCommand(request.events().stream()
      .map(item -> new RecordNavigationEventsCommand.Event(item.navigationSessionId(), item.siteId(),
        item.floorId(), item.eventType(), item.nodeReference(), item.occurredAt(), item.metadata()))
      .toList()));
  }

  @GetMapping("/navigation-summary")
  @PreAuthorize("hasRole('CLINIC_ADMIN') and @siteOwnerGuard.isOwner(authentication, #siteId)")
  @Operation(summary = "Consolidated indicators of a site in a period, for the dashboard (TS-30)")
  public NavigationSummaryResponse navigationSummary(@RequestParam UUID siteId,
                                                     @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant from,
                                                     @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant to) {
    DateRange period = new DateRange(from, to);
    NavigationMetrics metrics = getSummary.handle(new GetNavigationSummaryQuery(siteId, period));
    return new NavigationSummaryResponse(siteId, period.from(), period.to(), metrics.hasData(),
      metrics.startedSessions(), metrics.finishedSessions(), metrics.completionRate(),
      metrics.averageDurationSeconds(), metrics.averageDeviationsPerSession(), metrics.recalculationRate(),
      metrics.topDestinations().stream()
        .map(usage -> new DestinationUsageResponse(usage.destinationNodeReference(), usage.sessionCount()))
        .toList());
  }
}
