package com.uitopic.rumbo.communications.domain.model.valueobjects;

import com.uitopic.rumbo.communications.domain.model.aggregates.Notification;

import java.util.List;

/**
 * Answer of the notification center: a page plus the unread counter, which ignores the page filter.
 *
 * @param items notifications of the page, newest first
 * @param total notifications that match the filter across every page
 * @param unread every unread notification of the recipient
 */
public record NotificationListing(List<Notification> items, long total, long unread, int page, int size) {
  public NotificationListing {
    items = List.copyOf(items);
  }
}
