package com.uitopic.rumbo.shared.interfaces.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.time.Instant;
import java.util.Map;

/**
 * Builds error responses with the format shared by every bounded context (RFC 7807 Problem Details).
 * Each response carries a stable machine-readable {@code code} that clients use instead of parsing the message.
 */
public final class ProblemDetails {
  private ProblemDetails() {}

  public static ProblemDetail of(HttpStatus status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(status.getReasonPhrase());
    problem.setProperty("code", code);
    problem.setProperty("timestamp", Instant.now());
    return problem;
  }

  public static ProblemDetail withErrors(HttpStatus status, String code, String detail, Map<String, String> errors) {
    ProblemDetail problem = of(status, code, detail);
    problem.setProperty("errors", errors);
    return problem;
  }
}
