package com.uitopic.rumbo.iam.domain.model.queries;

import java.util.Objects;
import java.util.UUID;

public record GetUserAccountByIdQuery(UUID userId) {
  public GetUserAccountByIdQuery {
    Objects.requireNonNull(userId, "userId is required");
  }
}
