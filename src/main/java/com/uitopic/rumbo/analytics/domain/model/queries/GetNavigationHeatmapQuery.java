package com.uitopic.rumbo.analytics.domain.model.queries;

import com.uitopic.rumbo.analytics.domain.model.valueobjects.DateRange;

import java.util.Objects;
import java.util.UUID;

public record GetNavigationHeatmapQuery(UUID siteId, DateRange period) {
  public GetNavigationHeatmapQuery {
    Objects.requireNonNull(siteId, "siteId is required");
    Objects.requireNonNull(period, "period is required");
  }
}
