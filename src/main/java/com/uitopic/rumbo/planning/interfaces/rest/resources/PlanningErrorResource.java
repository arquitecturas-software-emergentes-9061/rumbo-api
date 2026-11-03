package com.uitopic.rumbo.planning.interfaces.rest.resources;

import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphElementType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphIssue;
import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphIssueCode;
import java.util.List;
import java.util.UUID;

/** Stable response for a failed Planning publication request. */
public record PlanningErrorResource(String code, String message, List<IssueResource> issues) {
  public PlanningErrorResource {
    issues = List.copyOf(issues);
  }

  /** A domain validation issue identified by element kind and ID. */
  public record IssueResource(GraphIssueCode code, GraphElementType elementType,
                              UUID elementId, String message) {
    public static IssueResource from(GraphIssue issue) {
      return new IssueResource(issue.code(), issue.elementType(), issue.elementId(), issue.message());
    }
  }
}
