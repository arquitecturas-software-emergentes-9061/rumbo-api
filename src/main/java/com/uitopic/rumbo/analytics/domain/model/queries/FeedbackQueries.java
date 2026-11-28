package com.uitopic.rumbo.analytics.domain.model.queries;

import com.uitopic.rumbo.analytics.domain.model.valueobjects.DateRange;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.IncidentCategory;
import java.util.Objects;
import java.util.UUID;

/** Queries on the feedback of the visitors (WI-TS31-06). The caller is authorized for {@code siteId} beforehand. */
public final class FeedbackQueries {
  public static final int DEFAULT_PAGE_SIZE = 20;
  public static final int MAX_PAGE_SIZE = 100;

  private FeedbackQueries() { }

  public record GetFeedbackSummaryQuery(UUID siteId, DateRange period) {
    public GetFeedbackSummaryQuery {
      Objects.requireNonNull(siteId, "siteId is required");
      Objects.requireNonNull(period, "period is required");
    }
  }

  /** @param category {@code null} for every category */
  public record ListIncidentReportsQuery(UUID siteId, DateRange period, IncidentCategory category, int page,
                                         int size) {
    public ListIncidentReportsQuery {
      Objects.requireNonNull(siteId, "siteId is required");
      Objects.requireNonNull(period, "period is required");
      if (page < 0) throw new IllegalArgumentException("page cannot be negative");
      if (size < 1 || size > MAX_PAGE_SIZE) {
        throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
      }
    }
  }

  public record GetSessionFeedbackQuery(UUID siteId, UUID navigationSessionId) {
    public GetSessionFeedbackQuery {
      Objects.requireNonNull(siteId, "siteId is required");
      Objects.requireNonNull(navigationSessionId, "navigationSessionId is required");
    }
  }
}
