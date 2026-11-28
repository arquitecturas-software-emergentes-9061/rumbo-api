package com.uitopic.rumbo.communications.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/**
 * Filter of the notification center. It always starts from the recipient, so no filter can reach the notifications
 * of another account or organization (WI-TS35-03).
 *
 * @param recipientUserId the only account whose notifications are read
 * @param unreadOnly keep only the unread ones
 * @param siteId keep only those about this site, or {@code null} for all
 * @param type keep only this type, or {@code null} for all
 * @param priority keep only this priority, or {@code null} for all
 */
public record NotificationFilter(UUID recipientUserId, boolean unreadOnly, UUID siteId, NotificationType type,
                                 NotificationPriority priority) {
  public NotificationFilter {
    Objects.requireNonNull(recipientUserId, "recipientUserId is required");
  }

  public static NotificationFilter of(UUID recipientUserId, boolean unreadOnly) {
    return new NotificationFilter(recipientUserId, unreadOnly, null, null, null);
  }
}
