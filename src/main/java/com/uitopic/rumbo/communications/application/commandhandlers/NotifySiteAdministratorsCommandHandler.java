package com.uitopic.rumbo.communications.application.commandhandlers;

import com.uitopic.rumbo.communications.application.ports.EmailNotificationSender;
import com.uitopic.rumbo.communications.application.ports.RecipientDirectory;
import com.uitopic.rumbo.communications.application.ports.SiteAdministrators;
import com.uitopic.rumbo.communications.domain.model.aggregates.Notification;
import com.uitopic.rumbo.communications.domain.model.commands.NotifySiteAdministratorsCommand;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationChannel;
import com.uitopic.rumbo.communications.domain.repositories.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the administrative notifications of a site (WI-US32-04, WI-US32-06, WI-TS35-04).
 *
 * <ul>
 *   <li>Recipients are the administrators of that site only, resolved when the event arrives.</li>
 *   <li>An event already notified to an administrator creates nothing, whether it is repeated now or later.</li>
 *   <li>With email delivery enabled and an address on file, the notification goes on the {@code EMAIL} channel and the
 *       retry job sends it; otherwise it is an {@code IN_APP} one. Either way it is listed in the center.</li>
 * </ul>
 */
@Service
public class NotifySiteAdministratorsCommandHandler {
  private final NotificationRepository notifications;
  private final ObjectProvider<SiteAdministrators> administrators;
  private final ObjectProvider<EmailNotificationSender> email;
  private final Clock clock;

  public NotifySiteAdministratorsCommandHandler(NotificationRepository notifications,
                                                ObjectProvider<SiteAdministrators> administrators,
                                                ObjectProvider<EmailNotificationSender> email, Clock clock) {
    this.notifications = notifications;
    this.administrators = administrators;
    this.email = email;
    this.clock = clock;
  }

  /** @return notifications created by this call */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int handle(NotifySiteAdministratorsCommand command) {
    SiteAdministrators resolver = administrators.getIfAvailable();
    if (resolver == null) return 0;
    boolean emailEnabled = email.getIfAvailable() != null;
    Instant now = clock.instant();
    int created = 0;
    for (RecipientDirectory.Contact admin : resolver.of(command.siteId())) {
      if (!admin.reachable() || !admin.isAdministrator()) continue;
      if (notifications.existsForSource(command.sourceEventId(), admin.userId())) continue;
      NotificationChannel channel = emailEnabled && admin.email() != null && !admin.email().isBlank()
          ? NotificationChannel.EMAIL : NotificationChannel.IN_APP;
      try {
        notifications.save(Notification.create(admin.userId(), command.type(), channel, command.detail(),
            command.siteId(), command.sourceEventId(), command.priority(), now));
        created++;
      } catch (DataIntegrityViolationException concurrentDuplicate) {
        // A concurrent delivery of the same event stored it first: the unique source index keeps that one, and this
        // transaction is rolled back without creating a second notification.
        throw new DuplicateNotificationException(concurrentDuplicate);
      }
    }
    return created;
  }

  /** The event was already notified by a concurrent delivery of it. */
  public static class DuplicateNotificationException extends RuntimeException {
    DuplicateNotificationException(Throwable cause) {
      super("The event was already notified", cause);
    }
  }
}
