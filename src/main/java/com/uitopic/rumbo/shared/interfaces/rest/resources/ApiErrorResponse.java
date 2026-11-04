package com.uitopic.rumbo.shared.interfaces.rest.resources;

/** Required fields of every Rumbo HTTP error response. */
public interface ApiErrorResponse {
  /** Stable machine-readable error code. */
  String code();

  /** Human-readable explanation safe to return to an API client. */
  String message();
}
