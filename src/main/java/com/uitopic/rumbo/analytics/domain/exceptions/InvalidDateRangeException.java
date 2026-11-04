package com.uitopic.rumbo.analytics.domain.exceptions;

/** The period of an analytics query is not valid (TS-30 scenario 3). The API answers 400 INVALID_DATE_RANGE. */
public class InvalidDateRangeException extends IllegalArgumentException {
  public static final String CODE = "INVALID_DATE_RANGE";

  public InvalidDateRangeException(String message) {
    super(message);
  }
}
