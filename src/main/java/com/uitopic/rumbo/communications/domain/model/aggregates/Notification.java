package com.uitopic.rumbo.communications.domain.model.aggregates;

import com.uitopic.rumbo.communications.domain.model.valueobjects.DeliveryStatus;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationChannel;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Communication addressed to one account. The account is referenced only by {@code recipientUserId}, so a user can
 * read nothing but their own notifications. It tracks both its delivery on a channel and whether the recipient read it.
 *
 * @param id identifier of the notification
 * @param recipientUserId IAM account that receives it
 * @param type event that produced it
 * @param channel medium of delivery
 * @param detail optional datum the client needs to render the text, at most {@value #MAX_DETAIL_LENGTH} characters
 * @param deliveryStatus state of the delivery on {@code channel}
 * @param deliveryAttempts attempts made so far
 * @param lastAttemptAt instant of the last attempt, or {@code null} before the first one
 * @param readAt instant the recipient read it, or {@code null} while unread
 * @param createdAt creation instant
 * @param updatedAt last modification instant
 */
public record Notification(UUID id, UUID recipientUserId, NotificationType type, NotificationChannel channel,
                           String detail, DeliveryStatus deliveryStatus, int deliveryAttempts, Instant lastAttemptAt,
                           Instant readAt, Instant createdAt, Instant updatedAt) {
  public static final int MAX_DELIVERY_ATTEMPTS = 3;
  public static final int MAX_DETAIL_LENGTH = 255;

  public Notification {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(recipientUserId, "recipientUserId is required");
    Objects.requireNonNull(type, "type is required");
    Objects.requireNonNull(channel, "channel is required");
    Objects.requireNonNull(deliveryStatus, "deliveryStatus is required");
    Objects.requireNonNull(createdAt, "createdAt is required");
    Objects.requireNonNull(updatedAt, "updatedAt is required");
    if (deliveryAttempts < 0) throw new IllegalArgumentException("deliveryAttempts cannot be negative");
    if (detail != null && detail.length() > MAX_DETAIL_LENGTH) throw new IllegalArgumentException("detail is too long");
  }

  /** A notification shown in the app: storing it is delivering it, so it is born {@code SENT} after one attempt. */
  public static Notification inApp(UUID recipientUserId, NotificationType type, String detail, Instant now) {
    return new Notification(UUID.randomUUID(), recipientUserId, type, NotificationChannel.IN_APP, detail,
        DeliveryStatus.SENT, 1, now, null, now, now);
  }

  /** A notification whose delivery has not been attempted yet. */
  public static Notification pending(UUID recipientUserId, NotificationType type, NotificationChannel channel,
                                     String detail, Instant now) {
    return new Notification(UUID.randomUUID(), recipientUserId, type, channel, detail, DeliveryStatus.PENDING, 0,
        null, null, now, now);
  }

  public boolean isRead() { return readAt != null; }

  /** Marks it read. Reading twice keeps the first instant. */
  public Notification markRead(Instant now) {
    if (isRead()) return this;
    return new Notification(id, recipientUserId, type, channel, detail, deliveryStatus, deliveryAttempts,
        lastAttemptAt, now, createdAt, now);
  }

  /** Records a successful attempt. */
  public Notification recordDelivered(Instant now) {
    requireOpenDelivery();
    return new Notification(id, recipientUserId, type, channel, detail, DeliveryStatus.SENT, deliveryAttempts + 1, now,
        readAt, createdAt, now);
  }

  /** Records a failed attempt: another retry stays scheduled until {@value #MAX_DELIVERY_ATTEMPTS} attempts failed. */
  public Notification recordFailedAttempt(Instant now) {
    requireOpenDelivery();
    int attempts = deliveryAttempts + 1;
    DeliveryStatus next = attempts >= MAX_DELIVERY_ATTEMPTS ? DeliveryStatus.FAILED : DeliveryStatus.RETRY_PENDING;
    return new Notification(id, recipientUserId, type, channel, detail, next, attempts, now, readAt, createdAt, now);
  }

  private void requireOpenDelivery() {
    if (deliveryStatus.isFinal()) throw new IllegalStateException("Delivery already finished: " + deliveryStatus);
  }
}
