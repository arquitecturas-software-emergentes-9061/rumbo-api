package com.uitopic.rumbo.profiles.infrastructure.persistence.jpa.repositories;

import com.uitopic.rumbo.profiles.infrastructure.persistence.jpa.entities.ProfileJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data access to {@code profiles_user_profiles}. */
public interface ProfileJpaRepository extends JpaRepository<ProfileJpaEntity, UUID> {
  boolean existsByUserId(UUID userId);

  Optional<ProfileJpaEntity> findByUserId(UUID userId);
}
