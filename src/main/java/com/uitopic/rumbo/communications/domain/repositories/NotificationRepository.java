package com.uitopic.rumbo.communications.domain.repositories;

import com.uitopic.rumbo.communications.domain.model.aggregates.Notification;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationSlice;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence contract of {@link Notification}. Every read is scoped to the recipient. */
public interface NotificationRepository {
  Notification save(Notification notification);

  /** The notification only if it belongs to {@code recipientUserId}. */
  Optional<Notification> findByIdAndRecipient(UUID id, UUID recipientUserId);

  /** One page, newest first. {@code page} starts at 0. */
  NotificationSlice findPage(UUID recipientUserId, boolean unreadOnly, int page, int size);

  long countUnread(UUID recipientUserId);

  /** Marks every unread notification of the recipient as read and returns how many changed. */
  int markAllRead(UUID recipientUserId, Instant now);
}
