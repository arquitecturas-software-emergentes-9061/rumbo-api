package com.uitopic.rumbo.communications.domain.model.aggregates;

import com.uitopic.rumbo.communications.domain.model.valueobjects.DeliveryStatus;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationChannel;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationPriority;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Communication addressed to one account. The account is referenced only by {@code recipientUserId}, so a user can
 * read nothing but their own notifications. It tracks both its delivery on a channel and whether the recipient read it.
 *
 * <p>Delivery lifecycle (WI-TS36-02): an {@code IN_APP} notification is delivered when it is stored, so it is born
 * {@code SENT}. Any other channel is born {@code PENDING}; a failed attempt moves it to {@code RETRY_PENDING} with the
 * next attempt scheduled after {@link #RETRY_BACKOFF}, until {@value #MAX_DELIVERY_ATTEMPTS} attempts failed and it
 * ends {@code FAILED}. {@code SENT} and {@code FAILED} are final.
 *
 * @param id identifier of the notification
 * @param recipientUserId IAM account that receives it
 * @param type event that produced it
 * @param channel medium of delivery
 * @param detail optional datum the client needs to render the text, at most {@value #MAX_DETAIL_LENGTH} characters
 * @param siteId site the notification is about, or {@code null} for an account notification
 * @param sourceEventId id of the event that produced it, so a repeated event creates nothing; {@code null} if unknown
 * @param priority how prominently the client shows it
 * @param deliveryStatus state of the delivery on {@code channel}
 * @param deliveryAttempts attempts made so far
 * @param lastAttemptAt instant of the last attempt, or {@code null} before the first one
 * @param nextAttemptAt instant from which the next attempt may run, or {@code null} once the delivery is final
 * @param readAt instant the recipient read it, or {@code null} while unread
 * @param createdAt creation instant
 * @param updatedAt last modification instant
 */
public record Notification(UUID id, UUID recipientUserId, NotificationType type, NotificationChannel channel,
                           String detail, UUID siteId, UUID sourceEventId, NotificationPriority priority,
                           DeliveryStatus deliveryStatus, int deliveryAttempts, Instant lastAttemptAt,
                           Instant nextAttemptAt, Instant readAt, Instant createdAt, Instant updatedAt) {
  public static final int MAX_DELIVERY_ATTEMPTS = 3;
  public static final int MAX_DETAIL_LENGTH = 255;
  /** Wait before the second and the third attempt. */
  public static final List<Duration> RETRY_BACKOFF = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5));

  public Notification {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(recipientUserId, "recipientUserId is required");
    Objects.requireNonNull(type, "type is required");
    Objects.requireNonNull(channel, "channel is required");
    Objects.requireNonNull(priority, "priority is required");
    Objects.requireNonNull(deliveryStatus, "deliveryStatus is required");
    Objects.requireNonNull(createdAt, "createdAt is required");
    Objects.requireNonNull(updatedAt, "updatedAt is required");
    if (deliveryAttempts < 0) throw new IllegalArgumentException("deliveryAttempts cannot be negative");
    if (detail != null && detail.length() > MAX_DETAIL_LENGTH) throw new IllegalArgumentException("detail is too long");
    if (deliveryStatus.isFinal()) nextAttemptAt = null;
  }

  /** A notification shown in the app: storing it is delivering it, so it is born {@code SENT} after one attempt. */
  public static Notification inApp(UUID recipientUserId, NotificationType type, String detail, Instant now) {
    return create(recipientUserId, type, NotificationChannel.IN_APP, detail, null, null, NotificationPriority.NORMAL,
        now);
  }

  /** A notification whose delivery has not been attempted yet. */
  public static Notification pending(UUID recipientUserId, NotificationType type, NotificationChannel channel,
                                     String detail, Instant now) {
    return new Notification(UUID.randomUUID(), recipientUserId, type, channel, detail, null, null,
        NotificationPriority.NORMAL, DeliveryStatus.PENDING, 0, null, now, null, now, now);
  }

  /**
   * A notification produced by a platform event. {@code IN_APP} is delivered on creation; any other channel waits for
   * its first attempt, which may run right away.
   */
  public static Notification create(UUID recipientUserId, NotificationType type, NotificationChannel channel,
                                    String detail, UUID siteId, UUID sourceEventId, NotificationPriority priority,
                                    Instant now) {
    if (channel == NotificationChannel.IN_APP) {
      return new Notification(UUID.randomUUID(), recipientUserId, type, channel, detail, siteId, sourceEventId,
          priority, DeliveryStatus.SENT, 1, now, null, null, now, now);
    }
    return new Notification(UUID.randomUUID(), recipientUserId, type, channel, detail, siteId, sourceEventId, priority,
        DeliveryStatus.PENDING, 0, null, now, null, now, now);
  }

  public boolean isRead() { return readAt != null; }

  /** Whether a delivery attempt may run at {@code now}. */
  public boolean isDue(Instant now) {
    return !deliveryStatus.isFinal() && nextAttemptAt != null && !nextAttemptAt.isAfter(now);
  }

  /** Marks it read. Reading twice keeps the first instant. */
  public Notification markRead(Instant now) {
    if (isRead()) return this;
    return new Notification(id, recipientUserId, type, channel, detail, siteId, sourceEventId, priority,
        deliveryStatus, deliveryAttempts, lastAttemptAt, nextAttemptAt, now, createdAt, now);
  }

  /** Records a successful attempt. */
  public Notification recordDelivered(Instant now) {
    requireOpenDelivery();
    return new Notification(id, recipientUserId, type, channel, detail, siteId, sourceEventId, priority,
        DeliveryStatus.SENT, deliveryAttempts + 1, now, null, readAt, createdAt, now);
  }

  /**
   * Records a failed attempt that may succeed later: another attempt is scheduled until
   * {@value #MAX_DELIVERY_ATTEMPTS} attempts failed.
   */
  public Notification recordFailedAttempt(Instant now) {
    requireOpenDelivery();
    int attempts = deliveryAttempts + 1;
    if (attempts >= MAX_DELIVERY_ATTEMPTS) return finishFailed(attempts, now);
    Duration wait = RETRY_BACKOFF.get(Math.min(attempts, RETRY_BACKOFF.size()) - 1);
    return new Notification(id, recipientUserId, type, channel, detail, siteId, sourceEventId, priority,
        DeliveryStatus.RETRY_PENDING, attempts, now, now.plus(wait), readAt, createdAt, now);
  }

  /** Records an attempt that can never succeed, such as an unreachable recipient: no retry is scheduled. */
  public Notification recordPermanentFailure(Instant now) {
    requireOpenDelivery();
    return finishFailed(deliveryAttempts + 1, now);
  }

  private Notification finishFailed(int attempts, Instant now) {
    return new Notification(id, recipientUserId, type, channel, detail, siteId, sourceEventId, priority,
        DeliveryStatus.FAILED, attempts, now, null, readAt, createdAt, now);
  }

  private void requireOpenDelivery() {
    if (deliveryStatus.isFinal()) throw new IllegalStateException("Delivery already finished: " + deliveryStatus);
  }
}
