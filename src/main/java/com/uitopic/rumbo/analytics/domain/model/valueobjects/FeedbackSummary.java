package com.uitopic.rumbo.analytics.domain.model.valueobjects;

import java.util.List;

/**
 * Feedback of a site in a period (WI-TS31-06): ratings and incident reports.
 *
 * @param ratings ratings received
 * @param averageScore mean score, or {@code null} without ratings
 * @param scoreDistribution ratings per score, from 1 to 5, always five entries
 * @param incidents incident reports received
 * @param incidentsWithoutPosition reports that came without a position on the plan
 * @param incidentsByCategory reports per category, most frequent first, only categories with reports
 */
public record FeedbackSummary(long ratings, Double averageScore, List<ScoreCount> scoreDistribution, long incidents,
                              long incidentsWithoutPosition, List<CategoryCount> incidentsByCategory) {
  public FeedbackSummary {
    scoreDistribution = List.copyOf(scoreDistribution);
    incidentsByCategory = List.copyOf(incidentsByCategory);
  }

  public boolean hasData() { return ratings > 0 || incidents > 0; }

  public record ScoreCount(int score, long ratings) { }

  public record CategoryCount(IncidentCategory category, long incidents) { }
}
