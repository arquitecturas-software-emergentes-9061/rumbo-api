package com.uitopic.rumbo.communications.application.eventhandlers;

import com.uitopic.rumbo.analytics.domain.model.events.NavigationIncidentReportedEvent;
import com.uitopic.rumbo.communications.application.commandhandlers.NotifySiteAdministratorsCommandHandler;
import com.uitopic.rumbo.communications.domain.model.commands.NotifySiteAdministratorsCommand;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationPriority;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationType;
import com.uitopic.rumbo.planning.domain.model.events.MapVersionPublishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns events of the operation of a site into notifications for its administrators (WI-TS35-02): problems reported
 * by visitors (Analytics) and map publications (Planning). It depends only on the published event records, listens
 * after their commit, and a failure is logged here without touching the report or the publication.
 */
@Component
public class OperationalEventsNotificationHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(OperationalEventsNotificationHandler.class);

  private final NotifySiteAdministratorsCommandHandler commands;

  public OperationalEventsNotificationHandler(NotifySiteAdministratorsCommandHandler commands) {
    this.commands = commands;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void on(NavigationIncidentReportedEvent event) {
    notify(new NotifySiteAdministratorsCommand(event.siteId(), NotificationType.INCIDENT_REPORTED,
        event.category().name(), event.reportId(), NotificationPriority.HIGH));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void on(MapVersionPublishedEvent event) {
    notify(new NotifySiteAdministratorsCommand(event.siteId(), NotificationType.MAP_PUBLISHED,
        event.modelVersionId().toString(), event.modelVersionId(), NotificationPriority.NORMAL));
  }

  private void notify(NotifySiteAdministratorsCommand command) {
    try {
      commands.handle(command);
    } catch (NotifySiteAdministratorsCommandHandler.DuplicateNotificationException repeated) {
      LOGGER.debug("Event {} was already notified", command.sourceEventId());
    } catch (RuntimeException failure) {
      LOGGER.error("Could not notify the administrators of site {} about {}", command.siteId(), command.type(),
          failure);
    }
  }
}
