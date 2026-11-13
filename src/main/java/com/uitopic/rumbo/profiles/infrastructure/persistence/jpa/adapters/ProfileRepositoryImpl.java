package com.uitopic.rumbo.profiles.infrastructure.persistence.jpa.adapters;

import com.uitopic.rumbo.profiles.domain.model.aggregates.Profile;
import com.uitopic.rumbo.profiles.domain.repositories.ProfileRepository;
import com.uitopic.rumbo.profiles.infrastructure.persistence.jpa.mappers.ProfileJpaMapper;
import com.uitopic.rumbo.profiles.infrastructure.persistence.jpa.repositories.ProfileJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** JPA implementation of {@link ProfileRepository}. */
@Repository
public class ProfileRepositoryImpl implements ProfileRepository {
  private final ProfileJpaRepository jpa;

  public ProfileRepositoryImpl(ProfileJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public boolean existsByUserId(UUID userId) {
    return jpa.existsByUserId(userId);
  }

  /** Flushes immediately so a duplicate {@code user_id} fails here and not later at commit. */
  @Override
  public Profile save(Profile profile) {
    return ProfileJpaMapper.toDomain(jpa.saveAndFlush(ProfileJpaMapper.toEntity(profile)));
  }

  @Override
  public Optional<Profile> findByUserId(UUID userId) {
    return jpa.findByUserId(userId).map(ProfileJpaMapper::toDomain);
  }
}
