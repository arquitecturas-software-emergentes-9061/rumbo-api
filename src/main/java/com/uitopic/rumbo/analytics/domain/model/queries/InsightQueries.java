package com.uitopic.rumbo.analytics.domain.model.queries;

import com.uitopic.rumbo.analytics.domain.model.valueobjects.DateRange;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.TrendGranularity;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/** Queries of the navigation reports of the administrator (WI-US25-07, WI-US25-08). */
public final class InsightQueries {
  public static final String DEFAULT_ZONE = "America/Lima";
  public static final double DEFAULT_CELL_SIZE = 2.0;
  public static final double MAX_CELL_SIZE = 100.0;

  private InsightQueries() { }

  /** Indicators of one site per day or week. The period must fit in the points the granularity allows. */
  public record GetNavigationTrendQuery(UUID siteId, DateRange period, TrendGranularity granularity, ZoneId zone) {
    public GetNavigationTrendQuery {
      Objects.requireNonNull(siteId, "siteId is required");
      Objects.requireNonNull(period, "period is required");
      Objects.requireNonNull(granularity, "granularity is required");
      Objects.requireNonNull(zone, "zone is required");
      granularity.requireCovers(period);
    }

    public static ZoneId zone(String id) {
      try {
        return ZoneId.of(id == null || id.isBlank() ? DEFAULT_ZONE : id.trim());
      } catch (DateTimeException unknown) {
        throw new IllegalArgumentException("Unknown time zone: " + id);
      }
    }
  }

  /** @param floorId {@code null} for every floor */
  public record GetIncidentZonesQuery(UUID siteId, DateRange period, UUID floorId, double cellSize) {
    public GetIncidentZonesQuery {
      Objects.requireNonNull(siteId, "siteId is required");
      Objects.requireNonNull(period, "period is required");
      if (!(cellSize > 0) || cellSize > MAX_CELL_SIZE) {
        throw new IllegalArgumentException("cellSize must be greater than 0 and at most " + MAX_CELL_SIZE);
      }
    }
  }

  /** Activity of every site the administrator can see. */
  public record GetSitesOverviewQuery(UUID administratorUserId, DateRange period) {
    public GetSitesOverviewQuery {
      Objects.requireNonNull(administratorUserId, "administratorUserId is required");
      Objects.requireNonNull(period, "period is required");
    }
  }
}
