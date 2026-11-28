package com.uitopic.rumbo.analytics.domain.model.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A visitor rated a finished navigation session (TS-31). The registration of the rating publishes it once the rating
 * is stored; Analytics keeps an anonymized fact of it and never stores the rating a second time.
 *
 * @param ratingId identifier of the stored rating, used to discard a repeated event
 * @param navigationSessionId session that was rated
 * @param siteId site of the session
 * @param destinationNodeReference destination of the session, or {@code null} if unknown
 * @param score from {@value #MIN_SCORE} to {@value #MAX_SCORE}
 * @param ratedAt instant the visitor rated it
 */
public record NavigationRatedEvent(UUID ratingId, UUID navigationSessionId, UUID siteId,
                                   String destinationNodeReference, int score, Instant ratedAt) {
  public static final int MIN_SCORE = 1;
  public static final int MAX_SCORE = 5;

  public NavigationRatedEvent {
    Objects.requireNonNull(ratingId, "ratingId is required");
    Objects.requireNonNull(navigationSessionId, "navigationSessionId is required");
    Objects.requireNonNull(siteId, "siteId is required");
    Objects.requireNonNull(ratedAt, "ratedAt is required");
    if (score < MIN_SCORE || score > MAX_SCORE) {
      throw new IllegalArgumentException("score must be between " + MIN_SCORE + " and " + MAX_SCORE);
    }
    destinationNodeReference = destinationNodeReference == null || destinationNodeReference.isBlank()
      ? null : destinationNodeReference.trim();
  }
}
