package com.uitopic.rumbo.iam.domain.model.valueobjects;

import java.time.Duration;
import java.util.Objects;

/**
 * Lifetimes of the credentials issued when a session opens or rotates.
 *
 * @param accessTokenLifetime validity of the short-lived access token
 * @param refreshTokenLifetime validity of the refresh token, extended on every rotation
 */
public record SessionPolicy(Duration accessTokenLifetime, Duration refreshTokenLifetime) {
  public SessionPolicy {
    Objects.requireNonNull(accessTokenLifetime, "accessTokenLifetime is required");
    Objects.requireNonNull(refreshTokenLifetime, "refreshTokenLifetime is required");
    if (accessTokenLifetime.isNegative() || accessTokenLifetime.isZero()) {
      throw new IllegalArgumentException("accessTokenLifetime must be positive");
    }
    if (refreshTokenLifetime.compareTo(accessTokenLifetime) <= 0) {
      throw new IllegalArgumentException("refreshTokenLifetime must be longer than accessTokenLifetime");
    }
  }
}
