package com.uitopic.rumbo.analytics.domain.repositories;

import com.uitopic.rumbo.analytics.domain.model.aggregates.FeedbackFact;

/** Write side of the feedback facts. */
public interface FeedbackFactRepository {
  /**
   * Stores the fact unless one with the same source already exists, or its session already has a rating.
   *
   * @return {@code true} if it was stored, {@code false} if it was a repetition
   */
  boolean recordOnce(FeedbackFact fact);
}
