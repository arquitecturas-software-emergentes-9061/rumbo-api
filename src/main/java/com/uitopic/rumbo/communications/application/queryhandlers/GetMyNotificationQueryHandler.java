package com.uitopic.rumbo.communications.application.queryhandlers;

import com.uitopic.rumbo.communications.domain.exceptions.NotificationNotFoundException;
import com.uitopic.rumbo.communications.domain.model.aggregates.Notification;
import com.uitopic.rumbo.communications.domain.model.queries.GetMyNotificationQuery;
import com.uitopic.rumbo.communications.domain.repositories.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads one notification, and its delivery state, of the caller. Another account's id answers not found. */
@Service
public class GetMyNotificationQueryHandler {
  private final NotificationRepository notifications;

  public GetMyNotificationQueryHandler(NotificationRepository notifications) {
    this.notifications = notifications;
  }

  @Transactional(readOnly = true)
  public Notification handle(GetMyNotificationQuery query) {
    return notifications.findByIdAndRecipient(query.notificationId(), query.userId())
        .orElseThrow(NotificationNotFoundException::new);
  }
}
