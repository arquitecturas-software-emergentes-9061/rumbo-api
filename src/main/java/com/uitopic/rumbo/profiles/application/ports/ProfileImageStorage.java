package com.uitopic.rumbo.profiles.application.ports;

import java.util.UUID;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.ProfileImage;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.ProfileImageFile;

public interface ProfileImageStorage {
  ProfileImage store(UUID userId, ProfileImageFile file);
  /** Allows idempotent removal to report unavailable configuration even when there is no asset. */
  default void requireAvailable() {}
  void delete(String storageKey);
}
