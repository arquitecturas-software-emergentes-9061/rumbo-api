package com.uitopic.rumbo.iam.interfaces.rest.resources;

import java.time.Instant;
import java.util.UUID;

/**
 * Credentials returned by sign-in and refresh. {@code expiresIn} is the access-token lifetime in seconds; the client
 * calls {@code /api/v1/auth/refresh} with {@code refreshToken} before it runs out.
 */
public record AuthenticatedUserResponse(UUID userId, String role, UUID clinicId, String tokenType,
                                        String accessToken, long expiresIn, String refreshToken,
                                        Instant refreshTokenExpiresAt) {
  @Override
  public String toString() {
    return "AuthenticatedUserResponse[userId=" + userId + ", role=" + role + "]";
  }
}
