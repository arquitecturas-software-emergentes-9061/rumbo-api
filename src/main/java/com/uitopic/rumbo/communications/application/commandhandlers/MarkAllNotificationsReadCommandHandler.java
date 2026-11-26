package com.uitopic.rumbo.communications.application.commandhandlers;

import com.uitopic.rumbo.communications.domain.model.commands.MarkAllNotificationsReadCommand;
import com.uitopic.rumbo.communications.domain.repositories.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class MarkAllNotificationsReadCommandHandler {
  private final NotificationRepository notifications;
  private final Clock clock;

  public MarkAllNotificationsReadCommandHandler(NotificationRepository notifications, Clock clock) {
    this.notifications = notifications;
    this.clock = clock;
  }

  /** Returns how many notifications changed. */
  @Transactional
  public int handle(MarkAllNotificationsReadCommand command) {
    return notifications.markAllRead(command.userId(), clock.instant());
  }
}
