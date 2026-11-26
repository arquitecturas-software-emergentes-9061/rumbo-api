package com.uitopic.rumbo.communications.application.queryhandlers;

import com.uitopic.rumbo.communications.domain.model.queries.ListMyNotificationsQuery;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationListing;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationSlice;
import com.uitopic.rumbo.communications.domain.repositories.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListMyNotificationsQueryHandler {
  private final NotificationRepository notifications;

  public ListMyNotificationsQueryHandler(NotificationRepository notifications) {
    this.notifications = notifications;
  }

  @Transactional(readOnly = true)
  public NotificationListing handle(ListMyNotificationsQuery query) {
    NotificationSlice slice = notifications.findPage(query.userId(), query.unreadOnly(), query.page(), query.size());
    return new NotificationListing(slice.items(), slice.total(), notifications.countUnread(query.userId()),
        query.page(), query.size());
  }
}
