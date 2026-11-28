package com.uitopic.rumbo.analytics.domain.model.valueobjects;

import com.uitopic.rumbo.analytics.domain.exceptions.InvalidDateRangeException;
import java.time.Duration;

/** Length of each point of a navigation trend (WI-US25-07), with the longest period it can cover. */
public enum TrendGranularity {
  DAY("day", Duration.ofDays(1), 93),
  WEEK("week", Duration.ofDays(7), 53);

  private final String sqlUnit;
  private final Duration length;
  private final int maxPoints;

  TrendGranularity(String sqlUnit, Duration length, int maxPoints) {
    this.sqlUnit = sqlUnit;
    this.length = length;
    this.maxPoints = maxPoints;
  }

  /** Unit understood by PostgreSQL {@code date_trunc}. */
  public String sqlUnit() { return sqlUnit; }

  public int maxPoints() { return maxPoints; }

  /** Rejects a period that would need more than {@link #maxPoints()} points. */
  public void requireCovers(DateRange period) {
    Duration longest = length.multipliedBy(maxPoints - 1L);
    if (Duration.between(period.from(), period.to()).compareTo(longest) > 0) {
      throw new InvalidDateRangeException("A trend by " + sqlUnit + " covers at most " + (maxPoints - 1) + " "
        + sqlUnit + "s; choose a longer granularity or a shorter period");
    }
  }
}
