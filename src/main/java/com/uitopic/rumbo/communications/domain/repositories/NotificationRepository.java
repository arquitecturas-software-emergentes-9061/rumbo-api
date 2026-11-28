package com.uitopic.rumbo.communications.domain.repositories;

import com.uitopic.rumbo.communications.domain.model.aggregates.Notification;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationFilter;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationSlice;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence contract of {@link Notification}. Every read of the center is scoped to the recipient. */
public interface NotificationRepository {
  Notification save(Notification notification);

  /** The notification only if it belongs to {@code recipientUserId}. */
  Optional<Notification> findByIdAndRecipient(UUID id, UUID recipientUserId);

  /** One page, newest first. {@code page} starts at 0. */
  default NotificationSlice findPage(UUID recipientUserId, boolean unreadOnly, int page, int size) {
    return findPage(NotificationFilter.of(recipientUserId, unreadOnly), page, size);
  }

  /** One page of the recipient of the filter, newest first. {@code page} starts at 0. */
  NotificationSlice findPage(NotificationFilter filter, int page, int size);

  long countUnread(UUID recipientUserId);

  /** Marks every unread notification of the recipient as read and returns how many changed. */
  int markAllRead(UUID recipientUserId, Instant now);

  /** Whether the event already produced a notification for the account. */
  boolean existsForSource(UUID sourceEventId, UUID recipientUserId);

  /**
   * Deliveries due at {@code now}, oldest schedule first, locked until the current transaction ends. Rows locked by
   * another worker are skipped, so two workers never attempt the same delivery.
   */
  List<Notification> lockDueDeliveries(Instant now, int limit);
}
