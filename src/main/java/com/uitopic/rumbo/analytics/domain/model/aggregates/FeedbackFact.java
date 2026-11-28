package com.uitopic.rumbo.analytics.domain.model.aggregates;

import com.uitopic.rumbo.analytics.domain.model.events.NavigationIncidentReportedEvent;
import com.uitopic.rumbo.analytics.domain.model.events.NavigationRatedEvent;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.FeedbackKind;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.IncidentCategory;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Anonymized analytic fact of a rating or an incident report (WI-TS31-05). It is identified by the id of the record
 * that produced it, so the same rating or report is counted once however many times its event arrives. It keeps no
 * visitor and no comment: only what the dashboard aggregates.
 *
 * @param sourceId id of the rating or report
 * @param kind rating or incident
 * @param navigationSessionId session, or {@code null} for an incident reported outside a trip
 * @param siteId site
 * @param floorId floor, or {@code null}
 * @param nodeReference node (the destination of a rating), or {@code null}
 * @param score rating score, only for {@link FeedbackKind#RATING}
 * @param category kind of problem, only for {@link FeedbackKind#INCIDENT}
 * @param x position on the floor plan, or {@code null}
 * @param y position on the floor plan, or {@code null}
 * @param occurredAt instant of the rating or report
 */
public record FeedbackFact(UUID sourceId, FeedbackKind kind, UUID navigationSessionId, UUID siteId, UUID floorId,
                           String nodeReference, Integer score, IncidentCategory category, Double x, Double y,
                           Instant occurredAt) {
  public FeedbackFact {
    Objects.requireNonNull(sourceId, "sourceId is required");
    Objects.requireNonNull(kind, "kind is required");
    Objects.requireNonNull(siteId, "siteId is required");
    Objects.requireNonNull(occurredAt, "occurredAt is required");
    if (kind == FeedbackKind.RATING && (score == null || category != null || navigationSessionId == null)) {
      throw new IllegalArgumentException("A rating has a session, a score and no category");
    }
    if (kind == FeedbackKind.INCIDENT && (category == null || score != null)) {
      throw new IllegalArgumentException("An incident has a category and no score");
    }
  }

  public static FeedbackFact of(NavigationRatedEvent event) {
    return new FeedbackFact(event.ratingId(), FeedbackKind.RATING, event.navigationSessionId(), event.siteId(), null,
      event.destinationNodeReference(), event.score(), null, null, null, event.ratedAt());
  }

  public static FeedbackFact of(NavigationIncidentReportedEvent event) {
    return new FeedbackFact(event.reportId(), FeedbackKind.INCIDENT, event.navigationSessionId(), event.siteId(),
      event.floorId(), event.nodeReference(), null, event.category(), event.x(), event.y(), event.reportedAt());
  }
}
