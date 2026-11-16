package com.uitopic.rumbo.analytics.domain.exceptions;

/** Metrics cannot be calculated right now. The API answers 503 METRICS_UNAVAILABLE and the client may retry. */
public class MetricsUnavailableException extends RuntimeException {
  public static final String CODE = "METRICS_UNAVAILABLE";

  public MetricsUnavailableException(String message) {
    super(message);
  }
}
