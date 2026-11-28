package com.uitopic.rumbo.communications.interfaces.rest.resources;

import com.uitopic.rumbo.communications.application.templates.NotificationTemplates;
import com.uitopic.rumbo.communications.domain.model.aggregates.Notification;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * A notification as the notification center shows it. {@code detail} is a datum, never display text; {@code title}
 * and {@code message} are the template rendered in the caller's language (Spanish unless {@code Accept-Language}
 * asks for English).
 */
public record NotificationResponse(UUID id, String type, String channel, String priority, UUID siteId, String title,
                                   String message, String detail, boolean read, Instant readAt, Instant createdAt) {
  public static NotificationResponse from(Notification notification) {
    return from(notification, NotificationTemplates.DEFAULT_LOCALE);
  }

  public static NotificationResponse from(Notification notification, Locale locale) {
    NotificationTemplates.Rendered text = NotificationTemplates.render(notification, locale);
    return new NotificationResponse(notification.id(), notification.type().name(), notification.channel().name(),
        notification.priority().name(), notification.siteId(), text.title(), text.message(), notification.detail(),
        notification.isRead(), notification.readAt(), notification.createdAt());
  }
}
