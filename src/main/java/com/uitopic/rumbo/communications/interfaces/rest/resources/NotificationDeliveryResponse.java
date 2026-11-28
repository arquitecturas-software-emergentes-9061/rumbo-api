package com.uitopic.rumbo.communications.interfaces.rest.resources;

import com.uitopic.rumbo.communications.domain.model.aggregates.Notification;

import java.time.Instant;
import java.util.UUID;

/**
 * Delivery state of a notification on its channel: {@code SENT}, {@code PENDING}, {@code RETRY_PENDING} or
 * {@code FAILED}. {@code finalState} is true once no further attempt will be made, and then {@code nextAttemptAt}
 * is {@code null} (WI-TS36-01).
 */
public record NotificationDeliveryResponse(UUID notificationId, String channel, String status, boolean finalState,
                                           int attempts, Instant lastAttemptAt, Instant nextAttemptAt) {
  public static NotificationDeliveryResponse from(Notification notification) {
    return new NotificationDeliveryResponse(notification.id(), notification.channel().name(),
        notification.deliveryStatus().name(), notification.deliveryStatus().isFinal(),
        notification.deliveryAttempts(), notification.lastAttemptAt(), notification.nextAttemptAt());
  }
}
