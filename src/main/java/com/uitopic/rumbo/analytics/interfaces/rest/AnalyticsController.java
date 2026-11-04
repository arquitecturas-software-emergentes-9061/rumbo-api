package com.uitopic.rumbo.analytics.interfaces.rest;

import com.uitopic.rumbo.analytics.application.commandhandlers.RecordNavigationEventsCommandHandler;
import com.uitopic.rumbo.analytics.domain.model.commands.RecordNavigationEventsCommand;
import com.uitopic.rumbo.analytics.interfaces.rest.resources.RecordNavigationEventsRequest;
import com.uitopic.rumbo.shared.interfaces.rest.openapi.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/analytics", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Analytics", description = "Navigation telemetry and dashboard metrics")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public class AnalyticsController {
  private final RecordNavigationEventsCommandHandler recordEvents;

  public AnalyticsController(RecordNavigationEventsCommandHandler recordEvents) {
    this.recordEvents = recordEvents;
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
}
