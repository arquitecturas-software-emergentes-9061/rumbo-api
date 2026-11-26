package com.uitopic.rumbo.profiles.domain.model.queries;

import java.util.Objects;
import java.util.UUID;

public record GetMyProfileQuery(UUID userId) {
  public GetMyProfileQuery {
    Objects.requireNonNull(userId, "userId is required");
  }
}
