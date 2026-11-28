package com.uitopic.rumbo.analytics.application.eventhandlers;

import com.uitopic.rumbo.analytics.application.commandhandlers.RecordFeedbackFactCommandHandler;
import com.uitopic.rumbo.analytics.domain.model.aggregates.FeedbackFact;
import com.uitopic.rumbo.analytics.domain.model.events.NavigationIncidentReportedEvent;
import com.uitopic.rumbo.analytics.domain.model.events.NavigationRatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Integrates the feedback of the visitors with Analytics (WI-TS31-05). It listens once the rating or report has been
 * stored, so a rolled-back registration leaves no fact, and a failure here is logged without undoing the visitor's
 * feedback. A repeated event is ignored.
 */
@Component
public class NavigationFeedbackEventHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(NavigationFeedbackEventHandler.class);

  private final RecordFeedbackFactCommandHandler commands;

  public NavigationFeedbackEventHandler(RecordFeedbackFactCommandHandler commands) {
    this.commands = commands;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void on(NavigationRatedEvent event) {
    record(FeedbackFact.of(event));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void on(NavigationIncidentReportedEvent event) {
    record(FeedbackFact.of(event));
  }

  private void record(FeedbackFact fact) {
    try {
      if (!commands.handle(fact)) LOGGER.debug("Ignored repeated {} {}", fact.kind(), fact.sourceId());
    } catch (RuntimeException failure) {
      LOGGER.error("Could not record the {} fact {} of site {}", fact.kind(), fact.sourceId(), fact.siteId(), failure);
    }
  }
}
