package com.uitopic.rumbo.communications.interfaces.rest.resources;

import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationListing;

import java.util.List;

public record NotificationListResponse(List<NotificationResponse> items, int page, int size, long total, long unread) {
  public static NotificationListResponse from(NotificationListing listing) {
    return new NotificationListResponse(listing.items().stream().map(NotificationResponse::from).toList(),
        listing.page(), listing.size(), listing.total(), listing.unread());
  }
}
