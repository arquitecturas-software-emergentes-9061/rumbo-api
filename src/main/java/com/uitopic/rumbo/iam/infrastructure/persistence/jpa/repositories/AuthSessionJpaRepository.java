package com.uitopic.rumbo.iam.infrastructure.persistence.jpa.repositories;

import com.uitopic.rumbo.iam.infrastructure.persistence.jpa.entities.AuthSessionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionJpaRepository extends JpaRepository<AuthSessionJpaEntity, UUID> {
  Optional<AuthSessionJpaEntity> findByRefreshTokenHash(String refreshTokenHash);

  @Query("""
    select s from AuthSessionJpaEntity s
    where s.userId = :userId and s.revokedAt is null and s.expiresAt > :now
    order by s.createdAt desc""")
  List<AuthSessionJpaEntity> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update AuthSessionJpaEntity s set s.revokedAt = :now where s.id in :ids and s.revokedAt is null")
  int revokeAll(@Param("ids") Collection<UUID> ids, @Param("now") Instant now);
}
