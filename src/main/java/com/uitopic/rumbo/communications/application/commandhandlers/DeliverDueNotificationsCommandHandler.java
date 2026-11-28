package com.uitopic.rumbo.communications.application.commandhandlers;

import com.uitopic.rumbo.communications.application.ports.EmailNotificationSender;
import com.uitopic.rumbo.communications.application.ports.RecipientDirectory;
import com.uitopic.rumbo.communications.application.templates.NotificationTemplates;
import com.uitopic.rumbo.communications.domain.model.aggregates.Notification;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationChannel;
import com.uitopic.rumbo.communications.domain.repositories.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Attempts the deliveries that are due (WI-TS35-04, WI-TS36-02). Each due notification is locked while it is
 * attempted, so two workers never send it twice, and every attempt is recorded on it:
 *
 * <ul>
 *   <li>accepted by the mail server: {@code SENT};</li>
 *   <li>server down or not configured: a retry is scheduled, until the last attempt leaves it {@code FAILED};</li>
 *   <li>recipient missing, disabled or rejected for good: {@code FAILED} at once.</li>
 * </ul>
 */
@Service
public class DeliverDueNotificationsCommandHandler {
  public static final int BATCH_SIZE = 50;
  private static final Logger LOGGER = LoggerFactory.getLogger(DeliverDueNotificationsCommandHandler.class);

  private final NotificationRepository notifications;
  private final ObjectProvider<RecipientDirectory> directory;
  private final ObjectProvider<EmailNotificationSender> email;
  private final Clock clock;

  public DeliverDueNotificationsCommandHandler(NotificationRepository notifications,
                                               ObjectProvider<RecipientDirectory> directory,
                                               ObjectProvider<EmailNotificationSender> email, Clock clock) {
    this.notifications = notifications;
    this.directory = directory;
    this.email = email;
    this.clock = clock;
  }

  @Transactional
  public DeliveryRun handle() {
    Instant now = clock.instant();
    DeliveryRun run = DeliveryRun.EMPTY;
    for (Notification due : notifications.lockDueDeliveries(now, BATCH_SIZE)) {
      Notification attempted = attempt(due, now);
      notifications.save(attempted);
      run = run.add(attempted);
    }
    return run;
  }

  private Notification attempt(Notification notification, Instant now) {
    if (notification.channel() == NotificationChannel.IN_APP) return notification.recordDelivered(now);
    EmailNotificationSender sender = email.getIfAvailable();
    if (sender == null) return notification.recordFailedAttempt(now);
    RecipientDirectory contacts = directory.getIfAvailable();
    Optional<RecipientDirectory.Contact> contact = contacts == null ? Optional.empty()
        : contacts.contactOf(notification.recipientUserId());
    if (contact.isEmpty() || !contact.get().reachable() || contact.get().email() == null) {
      return notification.recordPermanentFailure(now);
    }
    NotificationTemplates.Rendered text = NotificationTemplates.render(notification, Locale.forLanguageTag("es"));
    try {
      sender.send(contact.get().email(), "Rumbo — " + text.title(), text.message());
      return notification.recordDelivered(now);
    } catch (EmailNotificationSender.DeliveryFailure failure) {
      LOGGER.warn("Email delivery of notification {} failed (temporary: {})", notification.id(), failure.temporary());
      return failure.temporary() ? notification.recordFailedAttempt(now) : notification.recordPermanentFailure(now);
    }
  }

  /** Outcome of one run of the job. */
  public record DeliveryRun(int sent, int retryScheduled, int failed) {
    public static final DeliveryRun EMPTY = new DeliveryRun(0, 0, 0);

    DeliveryRun add(Notification attempted) {
      return switch (attempted.deliveryStatus()) {
        case SENT -> new DeliveryRun(sent + 1, retryScheduled, failed);
        case RETRY_PENDING, PENDING -> new DeliveryRun(sent, retryScheduled + 1, failed);
        case FAILED -> new DeliveryRun(sent, retryScheduled, failed + 1);
      };
    }

    public int attempted() { return sent + retryScheduled + failed; }
  }
}
