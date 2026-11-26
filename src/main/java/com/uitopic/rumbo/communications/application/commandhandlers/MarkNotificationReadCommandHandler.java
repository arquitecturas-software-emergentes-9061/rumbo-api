package com.uitopic.rumbo.communications.application.commandhandlers;

import com.uitopic.rumbo.communications.domain.exceptions.NotificationNotFoundException;
import com.uitopic.rumbo.communications.domain.model.aggregates.Notification;
import com.uitopic.rumbo.communications.domain.model.commands.MarkNotificationReadCommand;
import com.uitopic.rumbo.communications.domain.repositories.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class MarkNotificationReadCommandHandler {
  private final NotificationRepository notifications;
  private final Clock clock;

  public MarkNotificationReadCommandHandler(NotificationRepository notifications, Clock clock) {
    this.notifications = notifications;
    this.clock = clock;
  }

  /** Idempotent: a notification already read is returned untouched. */
  @Transactional
  public Notification handle(MarkNotificationReadCommand command) {
    Notification notification = notifications.findByIdAndRecipient(command.notificationId(), command.userId())
        .orElseThrow(NotificationNotFoundException::new);
    if (notification.isRead()) return notification;
    return notifications.save(notification.markRead(clock.instant()));
  }
}
