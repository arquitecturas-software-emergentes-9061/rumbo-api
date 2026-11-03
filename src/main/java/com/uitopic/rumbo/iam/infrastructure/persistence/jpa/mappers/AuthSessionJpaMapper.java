package com.uitopic.rumbo.iam.infrastructure.persistence.jpa.mappers;

import com.uitopic.rumbo.iam.domain.model.aggregates.AuthSession;
import com.uitopic.rumbo.iam.infrastructure.persistence.jpa.entities.AuthSessionJpaEntity;

/** Converts between the {@link AuthSession} aggregate and its persistence entity. */
public final class AuthSessionJpaMapper {
  private AuthSessionJpaMapper() {}

  public static AuthSessionJpaEntity toEntity(AuthSession session) {
    AuthSessionJpaEntity entity = new AuthSessionJpaEntity();
    entity.setId(session.id());
    entity.setUserId(session.userId());
    entity.setRefreshTokenHash(session.refreshTokenHash());
    entity.setExpiresAt(session.expiresAt());
    entity.setRevokedAt(session.revokedAt().orElse(null));
    entity.setCreatedAt(session.createdAt());
    return entity;
  }

  public static AuthSession toDomain(AuthSessionJpaEntity entity) {
    return AuthSession.restore(entity.getId(), entity.getUserId(), entity.getRefreshTokenHash(),
      entity.getExpiresAt(), entity.getRevokedAt(), entity.getCreatedAt());
  }
}
