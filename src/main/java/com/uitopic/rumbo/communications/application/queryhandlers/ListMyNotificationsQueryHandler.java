package com.uitopic.rumbo.communications.application.queryhandlers;

import com.uitopic.rumbo.communications.domain.model.queries.FilterMyNotificationsQuery;
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
    return handle(FilterMyNotificationsQuery.of(query.userId(), query.unreadOnly(), null, null, null, query.page(),
        query.size()));
  }

  /**
   * Filtered notification center (WI-TS35-03). The unread counter always covers every notification of the caller,
   * so the badge does not change when a filter is applied.
   */
  @Transactional(readOnly = true)
  public NotificationListing handle(FilterMyNotificationsQuery query) {
    NotificationSlice slice = notifications.findPage(query.filter(), query.page(), query.size());
    return new NotificationListing(slice.items(), slice.total(), notifications.countUnread(query.userId()),
        query.page(), query.size());
  }
}
