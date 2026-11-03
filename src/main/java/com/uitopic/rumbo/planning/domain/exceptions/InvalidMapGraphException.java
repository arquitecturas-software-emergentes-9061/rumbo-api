package com.uitopic.rumbo.planning.domain.exceptions;

import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphIssue;
import java.util.Objects;

/** Rejection of a draft edit that would violate an immediate graph invariant. */
public final class InvalidMapGraphException extends IllegalArgumentException {
  private final GraphIssue issue;

  public InvalidMapGraphException(GraphIssue issue) {
    super(Objects.requireNonNull(issue, "issue is required").message());
    this.issue = issue;
  }

  /** Structured reason suitable for an API adapter to translate into an error response. */
  public GraphIssue issue() {
    return issue;
  }
}
