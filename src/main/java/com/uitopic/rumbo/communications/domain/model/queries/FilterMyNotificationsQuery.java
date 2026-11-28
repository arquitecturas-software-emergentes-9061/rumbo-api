package com.uitopic.rumbo.communications.domain.model.queries;

import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationFilter;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationPriority;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationType;
import java.util.UUID;

/**
 * Notification center of the caller with optional filters (WI-TS35-03). The filters narrow the caller's own
 * notifications; none of them can widen the read to another account.
 *
 * @param siteId only those about this site, or {@code null}
 * @param type only this type, or {@code null}
 * @param priority only this priority, or {@code null}
 */
public record FilterMyNotificationsQuery(UUID userId, boolean unreadOnly, UUID siteId, NotificationType type,
                                         NotificationPriority priority, int page, int size) {

  /** Builds the query with out-of-range paging brought back into range, like {@link ListMyNotificationsQuery}. */
  public static FilterMyNotificationsQuery of(UUID userId, boolean unreadOnly, UUID siteId, NotificationType type,
                                              NotificationPriority priority, int page, int size) {
    ListMyNotificationsQuery paging = ListMyNotificationsQuery.of(userId, unreadOnly, page, size);
    return new FilterMyNotificationsQuery(userId, unreadOnly, siteId, type, priority, paging.page(), paging.size());
  }

  public NotificationFilter filter() {
    return new NotificationFilter(userId, unreadOnly, siteId, type, priority);
  }
}
