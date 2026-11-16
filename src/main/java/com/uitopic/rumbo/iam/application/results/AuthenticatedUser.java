package com.uitopic.rumbo.iam.application.results;

import com.uitopic.rumbo.iam.domain.model.valueobjects.UserRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Credentials returned when a session opens or rotates. The refresh token is the only copy of the raw value:
 * the server keeps just its digest.
 */
public record AuthenticatedUser(UUID userId, UserRole role, UUID clinicId, UUID sessionId, String accessToken,
                                Instant accessTokenExpiresAt, String refreshToken, Instant refreshTokenExpiresAt) {
  @Override
  public String toString() {
    return "AuthenticatedUser[userId=" + userId + ", role=" + role + ", sessionId=" + sessionId + "]";
  }
}
