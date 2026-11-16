package com.uitopic.rumbo.analytics.domain.model.queries;

import com.uitopic.rumbo.analytics.domain.model.valueobjects.DateRange;

import java.util.Objects;
import java.util.UUID;

/**
 * Indicators of one site in one period, for the dashboard (TS-30 scenario 2).
 *
 * @param siteId site whose sessions are summarized
 * @param period period of the summary
 */
public record GetNavigationSummaryQuery(UUID siteId, DateRange period) {
  public GetNavigationSummaryQuery {
    Objects.requireNonNull(siteId, "siteId is required");
    Objects.requireNonNull(period, "period is required");
  }
}
