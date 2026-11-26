package com.uitopic.rumbo.communications.domain.model.valueobjects;

import com.uitopic.rumbo.communications.domain.model.aggregates.Notification;

import java.util.List;

/**
 * One page of a recipient's notifications, newest first.
 *
 * @param items notifications of the page
 * @param total notifications that match the filter across every page
 */
public record NotificationSlice(List<Notification> items, long total) {
  public NotificationSlice {
    items = List.copyOf(items);
  }
}
