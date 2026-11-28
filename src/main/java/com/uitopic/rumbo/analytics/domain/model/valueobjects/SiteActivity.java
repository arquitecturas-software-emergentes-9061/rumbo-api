package com.uitopic.rumbo.analytics.domain.model.valueobjects;

import java.util.UUID;

/**
 * Indicators of one site of the administrator in a period (WI-US25-07), for the comparison between sites.
 *
 * @param completionRate sessions that arrived over sessions started, 0 without sessions
 * @param averageScore mean rating, or {@code null} without ratings
 */
public record SiteActivity(UUID siteId, String siteName, long startedSessions, long finishedSessions,
                           double completionRate, long incidents, long ratings, Double averageScore) {

  public static SiteActivity empty(UUID siteId, String siteName) {
    return new SiteActivity(siteId, siteName, 0, 0, 0, 0, 0, null);
  }

  public SiteActivity named(String name) {
    return new SiteActivity(siteId, name, startedSessions, finishedSessions, completionRate, incidents, ratings,
      averageScore);
  }
}
