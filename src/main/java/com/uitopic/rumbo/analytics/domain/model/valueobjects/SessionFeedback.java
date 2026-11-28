package com.uitopic.rumbo.analytics.domain.model.valueobjects;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Feedback of one navigation session of a site (WI-TS31-06).
 *
 * @param rating the rating of the session, or {@code null} if it was not rated
 * @param incidents reports made during the session, oldest first
 */
public record SessionFeedback(UUID navigationSessionId, UUID siteId, Rating rating, List<IncidentRecord> incidents) {
  public SessionFeedback {
    incidents = List.copyOf(incidents);
  }

  public record Rating(UUID ratingId, int score, String destinationNodeReference, Instant ratedAt) { }
}
