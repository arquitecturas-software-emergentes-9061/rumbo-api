package com.uitopic.rumbo.communications.domain.model.queries;

import java.util.UUID;

/**
 * @param userId caller; the only account whose notifications are read
 * @param unreadOnly keep only the unread ones
 * @param page zero-based page
 * @param size page size, between 1 and {@value #MAX_SIZE}
 */
public record ListMyNotificationsQuery(UUID userId, boolean unreadOnly, int page, int size) {
  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  /** Builds the query with out-of-range paging brought back into range. */
  public static ListMyNotificationsQuery of(UUID userId, boolean unreadOnly, int page, int size) {
    return new ListMyNotificationsQuery(userId, unreadOnly, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE));
  }
}
