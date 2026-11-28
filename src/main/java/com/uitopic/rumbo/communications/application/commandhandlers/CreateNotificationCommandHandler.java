package com.uitopic.rumbo.communications.application.commandhandlers;

import com.uitopic.rumbo.communications.application.ports.RecipientDirectory;
import com.uitopic.rumbo.communications.domain.model.aggregates.Notification;
import com.uitopic.rumbo.communications.domain.model.commands.CreateNotificationCommand;
import com.uitopic.rumbo.communications.domain.repositories.NotificationRepository;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores the in-app notification of an account event. It runs in its own transaction, so it commits independently of
 * the event that triggered it and a failure here never reaches the account, subscription or payment that caused it.
 *
 * <p>Recipient rule (WI-US31-03): when IAM is available, an account that no longer exists or is disabled receives
 * nothing.
 */
@Service
public class CreateNotificationCommandHandler {
  private final NotificationRepository notifications;
  private final Clock clock;
  private final ObjectProvider<RecipientDirectory> directory;

  @Autowired
  public CreateNotificationCommandHandler(NotificationRepository notifications, Clock clock,
                                          ObjectProvider<RecipientDirectory> directory) {
    this.notifications = notifications;
    this.clock = clock;
    this.directory = directory;
  }

  /** Without a directory every recipient is accepted. */
  public CreateNotificationCommandHandler(NotificationRepository notifications, Clock clock) {
    this(notifications, clock, null);
  }

  /** @return {@code true} if the notification was stored, {@code false} if the recipient cannot receive it */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean handle(CreateNotificationCommand command) {
    RecipientDirectory contacts = directory == null ? null : directory.getIfAvailable();
    if (contacts != null && !contacts.contactOf(command.recipientUserId())
        .map(RecipientDirectory.Contact::reachable).orElse(false)) {
      return false;
    }
    notifications.save(Notification.inApp(command.recipientUserId(), command.type(), command.detail(),
        clock.instant()));
    return true;
  }
}
