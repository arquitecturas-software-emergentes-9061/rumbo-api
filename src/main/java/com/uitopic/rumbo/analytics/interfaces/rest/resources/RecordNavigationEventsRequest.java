package com.uitopic.rumbo.analytics.interfaces.rest.resources;

import com.uitopic.rumbo.analytics.application.commandhandlers.RecordNavigationEventsCommandHandler;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Body of {@code POST /api/v1/analytics/navigation-events}: telemetry generated on the device. */
public record RecordNavigationEventsRequest(
  @NotEmpty @Size(max = RecordNavigationEventsCommandHandler.MAX_BATCH_SIZE) List<@Valid @NotNull EventItem> events) {

  /** One fact of the trip. {@code metadata} accepts only the keys documented in {@code EventMetadata}. */
  public record EventItem(
    @NotNull UUID navigationSessionId,
    @NotNull UUID siteId,
    UUID floorId,
    @NotNull NavigationEventType eventType,
    @Size(max = 100) String nodeReference,
    @NotNull Instant occurredAt,
    @Schema(example = "{\"destinationNodeReference\": \"cardiologia-201\"}") Map<String, Object> metadata) {
  }
}
