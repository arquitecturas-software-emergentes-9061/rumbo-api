package com.uitopic.rumbo.iam.infrastructure.persistence.jpa.adapters;

import com.uitopic.rumbo.iam.domain.model.aggregates.AuthSession;
import com.uitopic.rumbo.iam.domain.repositories.AuthSessionRepository;
import com.uitopic.rumbo.iam.infrastructure.persistence.jpa.entities.AuthSessionJpaEntity;
import com.uitopic.rumbo.iam.infrastructure.persistence.jpa.mappers.AuthSessionJpaMapper;
import com.uitopic.rumbo.iam.infrastructure.persistence.jpa.repositories.AuthSessionJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL adapter of {@link AuthSessionRepository}. */
@Repository
public class AuthSessionRepositoryImpl implements AuthSessionRepository {
  private final AuthSessionJpaRepository jpa;

  public AuthSessionRepositoryImpl(AuthSessionJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public AuthSession save(AuthSession session) {
    return AuthSessionJpaMapper.toDomain(jpa.save(AuthSessionJpaMapper.toEntity(session)));
  }

  @Override
  public Optional<AuthSession> findById(UUID id) {
    return jpa.findById(id).map(AuthSessionJpaMapper::toDomain);
  }

  @Override
  public Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash) {
    return jpa.findByRefreshTokenHash(refreshTokenHash).map(AuthSessionJpaMapper::toDomain);
  }

  @Override
  public List<AuthSession> findActiveByUserId(UUID userId, Instant now) {
    return jpa.findActiveByUserId(userId, now).stream().map(AuthSessionJpaMapper::toDomain).toList();
  }

  @Override
  public List<UUID> revokeAllByUserId(UUID userId, Instant now) {
    return revokeActive(userId, null, now);
  }

  @Override
  public List<UUID> revokeAllByUserIdExcept(UUID userId, UUID keptSessionId, Instant now) {
    return revokeActive(userId, keptSessionId, now);
  }

  private List<UUID> revokeActive(UUID userId, UUID keptSessionId, Instant now) {
    List<UUID> ids = jpa.findActiveByUserId(userId, now).stream().map(AuthSessionJpaEntity::getId)
      .filter(id -> !id.equals(keptSessionId)).toList();
    if (!ids.isEmpty()) jpa.revokeAll(ids, now);
    return ids;
  }
}
