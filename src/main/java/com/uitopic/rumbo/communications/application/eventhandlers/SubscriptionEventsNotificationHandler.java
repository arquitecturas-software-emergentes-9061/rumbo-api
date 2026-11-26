package com.uitopic.rumbo.communications.application.eventhandlers;

import com.uitopic.rumbo.communications.application.commandhandlers.CreateNotificationCommandHandler;
import com.uitopic.rumbo.communications.domain.model.commands.CreateNotificationCommand;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationType;
import com.uitopic.rumbo.subscriptions.domain.model.events.SubscriptionActivatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Notifies the owner of a subscription once the payment that activated it has committed. */
@Component
public class SubscriptionEventsNotificationHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionEventsNotificationHandler.class);

  private final CreateNotificationCommandHandler commands;

  public SubscriptionEventsNotificationHandler(CreateNotificationCommandHandler commands) {
    this.commands = commands;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(SubscriptionActivatedEvent event) {
    try {
      commands.handle(new CreateNotificationCommand(event.ownerUserId(), NotificationType.SUBSCRIPTION_ACTIVATED,
          event.planCode().name()));
    } catch (RuntimeException failure) {
      LOGGER.error("Could not create subscription notification for user {}", event.ownerUserId(), failure);
    }
  }
}
