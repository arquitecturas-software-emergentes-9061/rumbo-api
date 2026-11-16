package com.uitopic.rumbo.planning.domain.model.valueobjects;

import java.util.List;
import java.util.Objects;

/** Immutable set of defects found in a draft graph before publication. */
public record GraphValidationResult(List<GraphIssue> issues) {
  public GraphValidationResult {
    issues = List.copyOf(Objects.requireNonNull(issues, "issues are required"));
  }

  /** Whether the graph satisfies the currently supported publication rules. */
  public boolean isValid() {
    return issues.isEmpty();
  }
}
