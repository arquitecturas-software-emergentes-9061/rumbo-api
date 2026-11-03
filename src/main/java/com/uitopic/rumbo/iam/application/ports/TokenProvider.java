package com.uitopic.rumbo.iam.application.ports;

import com.uitopic.rumbo.iam.domain.model.valueobjects.UserRole;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Outbound port to issue and verify access tokens without depending on their format (JWT today). */
public interface TokenProvider {
  IssuedAccessToken issue(AccessTokenClaims claims, Duration lifetime);

  /** Verifies signature, issuer and expiry. Returns empty for any token that cannot be trusted. */
  Optional<VerifiedAccessToken> verify(String token);

  /**
   * Identity carried by an access token.
   *
   * @param userId account that owns the token
   * @param role role of the account
   * @param sessionId session the token belongs to; revoking the session invalidates the token
   * @param clinicId clinic administered by the account, {@code null} for patients or before the clinic exists
   */
  record AccessTokenClaims(UUID userId, UserRole role, UUID sessionId, UUID clinicId) {
  }

  record IssuedAccessToken(String value, Instant expiresAt) {
  }

  record VerifiedAccessToken(AccessTokenClaims claims, Instant expiresAt) {
  }
}
