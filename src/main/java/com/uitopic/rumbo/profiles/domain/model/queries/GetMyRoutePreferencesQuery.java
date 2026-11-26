package com.uitopic.rumbo.profiles.domain.model.queries;

import java.util.Objects;
import java.util.UUID;

public record GetMyRoutePreferencesQuery(UUID userId) {
  public GetMyRoutePreferencesQuery { Objects.requireNonNull(userId, "userId is required"); }
}
