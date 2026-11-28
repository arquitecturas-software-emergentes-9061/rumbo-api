package com.uitopic.rumbo.analytics.application.commandhandlers;

import com.uitopic.rumbo.analytics.domain.model.aggregates.FeedbackFact;
import com.uitopic.rumbo.analytics.domain.repositories.FeedbackFactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the analytic fact of a rating or incident report (WI-TS31-05). It runs in its own transaction because it is
 * called after the registration committed, and it never writes the rating or report itself.
 */
@Service
public class RecordFeedbackFactCommandHandler {
  private final FeedbackFactRepository facts;

  public RecordFeedbackFactCommandHandler(FeedbackFactRepository facts) {
    this.facts = facts;
  }

  /** @return {@code true} if the fact was new, {@code false} if the event was a repetition */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean handle(FeedbackFact fact) {
    return facts.recordOnce(fact);
  }
}
