package com.uitopic.rumbo.communications.application.eventhandlers;

import com.uitopic.rumbo.communications.application.commandhandlers.CreateNotificationCommandHandler;
import com.uitopic.rumbo.communications.domain.model.commands.CreateNotificationCommand;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationType;
import com.uitopic.rumbo.iam.domain.model.events.UserAccountLockedEvent;
import com.uitopic.rumbo.iam.domain.model.events.UserPasswordChangedEvent;
import com.uitopic.rumbo.iam.domain.model.events.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns IAM account events into notifications for the account owner. It listens after the commit, and a failure is
 * logged here: it never reaches the sign-up, lock or password change that already happened.
 */
@Component
public class AccountEventsNotificationHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(AccountEventsNotificationHandler.class);

  private final CreateNotificationCommandHandler commands;

  public AccountEventsNotificationHandler(CreateNotificationCommandHandler commands) {
    this.commands = commands;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(UserRegisteredEvent event) {
    notify(new CreateNotificationCommand(event.userId(), NotificationType.WELCOME, null));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(UserAccountLockedEvent event) {
    notify(new CreateNotificationCommand(event.userId(), NotificationType.ACCOUNT_LOCKED,
        event.lockedUntil().toString()));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(UserPasswordChangedEvent event) {
    notify(new CreateNotificationCommand(event.userId(), NotificationType.PASSWORD_CHANGED, null));
  }

  private void notify(CreateNotificationCommand command) {
    try {
      commands.handle(command);
    } catch (RuntimeException failure) {
      LOGGER.error("Could not create {} notification for user {}", command.type(), command.recipientUserId(), failure);
    }
  }
}
