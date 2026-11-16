package com.uitopic.rumbo.iam.domain.model.aggregates;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Aggregate root for a session issued to an account. It references the account only by identifier and keeps the
 * refresh token as a digest, never in plain text. A revoked or expired session cannot be rotated.
 */
public class AuthSession {
  private final UUID id;
  private final UUID userId;
  private String refreshTokenHash;
  private Instant expiresAt;
  private Instant revokedAt;
  private final Instant createdAt;

  private AuthSession(UUID id, UUID userId, String refreshTokenHash, Instant expiresAt, Instant revokedAt,
                      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id is required");
    this.userId = Objects.requireNonNull(userId, "userId is required");
    this.refreshTokenHash = requireHash(refreshTokenHash);
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt is required");
    this.revokedAt = revokedAt;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
  }

  /** Opens a session that stays valid for {@code lifetime} unless it is revoked. */
  public static AuthSession open(UUID id, UUID userId, String refreshTokenHash, Duration lifetime, Instant now) {
    requireLifetime(lifetime);
    return new AuthSession(id, userId, refreshTokenHash, now.plus(lifetime), null, now);
  }

  /** Rebuilds a session read from persistence. */
  public static AuthSession restore(UUID id, UUID userId, String refreshTokenHash, Instant expiresAt,
                                    Instant revokedAt, Instant createdAt) {
    return new AuthSession(id, userId, refreshTokenHash, expiresAt, revokedAt, createdAt);
  }

  public boolean isActive(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }

  /**
   * Replaces the refresh token and extends the session. The previous token stops working at once, so a stolen
   * token can be used at most until its legitimate owner refreshes.
   */
  public void rotate(String newRefreshTokenHash, Duration lifetime, Instant now) {
    if (!isActive(now)) throw new IllegalStateException("Only an active session can be rotated");
    requireLifetime(lifetime);
    refreshTokenHash = requireHash(newRefreshTokenHash);
    expiresAt = now.plus(lifetime);
  }

  /** Ends the session. Revoking twice keeps the first revocation instant. */
  public void revoke(Instant now) {
    if (revokedAt == null) revokedAt = now;
  }

  private static String requireHash(String hash) {
    if (hash == null || hash.isBlank()) throw new IllegalArgumentException("refreshTokenHash is required");
    return hash;
  }

  private static void requireLifetime(Duration lifetime) {
    if (lifetime == null || lifetime.isNegative() || lifetime.isZero()) {
      throw new IllegalArgumentException("Session lifetime must be positive");
    }
  }

  public UUID id() { return id; }
  public UUID userId() { return userId; }
  public String refreshTokenHash() { return refreshTokenHash; }
  public Instant expiresAt() { return expiresAt; }
  public Optional<Instant> revokedAt() { return Optional.ofNullable(revokedAt); }
  public Instant createdAt() { return createdAt; }
}
