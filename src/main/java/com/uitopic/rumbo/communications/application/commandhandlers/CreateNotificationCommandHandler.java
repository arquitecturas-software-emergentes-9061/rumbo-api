package com.uitopic.rumbo.communications.application.commandhandlers;

import com.uitopic.rumbo.communications.domain.model.aggregates.Notification;
import com.uitopic.rumbo.communications.domain.model.commands.CreateNotificationCommand;
import com.uitopic.rumbo.communications.domain.repositories.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Stores the in-app notification of a platform event. It runs in its own transaction, so it commits independently of
 * the event that triggered it and a failure here never reaches the account, subscription or payment that caused it.
 */
@Service
public class CreateNotificationCommandHandler {
  private final NotificationRepository notifications;
  private final Clock clock;

  public CreateNotificationCommandHandler(NotificationRepository notifications, Clock clock) {
    this.notifications = notifications;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(CreateNotificationCommand command) {
    notifications.save(Notification.inApp(command.recipientUserId(), command.type(), command.detail(), clock.instant()));
  }
}
