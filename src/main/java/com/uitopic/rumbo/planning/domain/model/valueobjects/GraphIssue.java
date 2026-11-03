package com.uitopic.rumbo.planning.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/**
 * A graph defect with the kind and ID of the affected element.
 * Graph-wide defects have no element ID; node and connection defects do.
 */
public record GraphIssue(GraphIssueCode code, GraphElementType elementType,
                         UUID elementId, String message) {
  public GraphIssue {
    Objects.requireNonNull(code, "code is required");
    Objects.requireNonNull(elementType, "elementType is required");
    Objects.requireNonNull(message, "message is required");
    if (message.isBlank()) {
      throw new IllegalArgumentException("Issue message must not be blank");
    }
    if ((elementType == GraphElementType.GRAPH) != (elementId == null)) {
      throw new IllegalArgumentException("Graph issues have no element ID; element issues require one");
    }
  }
}
