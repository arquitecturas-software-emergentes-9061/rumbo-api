package com.uitopic.rumbo.profiles.domain.repositories;

import com.uitopic.rumbo.profiles.domain.model.aggregates.Profile;

import java.util.Optional;
import java.util.UUID;

/** Persistence contract of {@link Profile}, independent of the storage technology. */
public interface ProfileRepository {
  boolean existsByUserId(UUID userId);

  /**
   * Stores the profile and returns it as persisted.
   *
   * @throws org.springframework.dao.DataIntegrityViolationException if the account already has a profile
   */
  Profile save(Profile profile);

  Optional<Profile> findByUserId(UUID userId);
}
