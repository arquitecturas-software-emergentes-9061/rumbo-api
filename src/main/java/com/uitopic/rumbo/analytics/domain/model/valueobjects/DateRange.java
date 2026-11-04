package com.uitopic.rumbo.analytics.domain.model.valueobjects;

import com.uitopic.rumbo.analytics.domain.exceptions.InvalidDateRangeException;

import java.time.Duration;
import java.time.Instant;

/**
 * Period of an analytics query, closed at {@code from} and open at {@code to}. The start cannot be after the end
 * (TS-30 scenario 3), and a period is at most {@link #MAX_LENGTH} long so one request cannot scan years of events.
 *
 * @param from first instant included
 * @param to first instant excluded
 */
public record DateRange(Instant from, Instant to) {
  public static final Duration MAX_LENGTH = Duration.ofDays(366);

  public DateRange {
    if (from == null || to == null) throw new InvalidDateRangeException("from and to are required");
    if (from.isAfter(to)) throw new InvalidDateRangeException("from cannot be after to");
    if (Duration.between(from, to).compareTo(MAX_LENGTH) > 0) {
      throw new InvalidDateRangeException("The period cannot be longer than " + MAX_LENGTH.toDays() + " days");
    }
  }

  public boolean contains(Instant instant) { return !instant.isBefore(from) && instant.isBefore(to); }
}
