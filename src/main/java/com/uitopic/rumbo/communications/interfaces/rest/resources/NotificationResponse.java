package com.uitopic.rumbo.communications.interfaces.rest.resources;

import com.uitopic.rumbo.communications.domain.model.aggregates.Notification;

import java.time.Instant;
import java.util.UUID;

/** A notification as the notification center shows it. {@code detail} is a datum, never display text. */
public record NotificationResponse(UUID id, String type, String channel, String detail, boolean read, Instant readAt,
                                   Instant createdAt) {
  public static NotificationResponse from(Notification notification) {
    return new NotificationResponse(notification.id(), notification.type().name(), notification.channel().name(),
        notification.detail(), notification.isRead(), notification.readAt(), notification.createdAt());
  }
}
