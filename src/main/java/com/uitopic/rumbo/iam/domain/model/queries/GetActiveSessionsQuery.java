package com.uitopic.rumbo.iam.domain.model.queries;

import java.util.Objects;
import java.util.UUID;

public record GetActiveSessionsQuery(UUID userId) {
  public GetActiveSessionsQuery {
    Objects.requireNonNull(userId, "userId is required");
  }
}
