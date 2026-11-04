package com.uitopic.rumbo.iam.domain.repositories;

import com.uitopic.rumbo.iam.domain.model.aggregates.AuthSession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence contract of {@link AuthSession}, independent of the storage technology. */
public interface AuthSessionRepository {
  AuthSession save(AuthSession session);

  Optional<AuthSession> findById(UUID id);

  /**
   * Finds the session that currently owns the given refresh token digest, if any, and keeps it locked until the
   * transaction ends, so two refreshes with the same token cannot both succeed.
   */
  Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

  /** Sessions of the account that are neither revoked nor expired at {@code now}. */
  List<AuthSession> findActiveByUserId(UUID userId, Instant now);

  /**
   * Revokes every open session of the account in a single statement.
   *
   * @return identifiers of the sessions that were revoked
   */
  List<UUID> revokeAllByUserId(UUID userId, Instant now);

  /**
   * Revokes every open session of the account except {@code keptSessionId}, for example the device where the user
   * just changed the password.
   *
   * @return identifiers of the sessions that were revoked
   */
  List<UUID> revokeAllByUserIdExcept(UUID userId, UUID keptSessionId, Instant now);
}
