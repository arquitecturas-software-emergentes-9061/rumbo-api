package com.uitopic.rumbo.profiles.application.commandhandlers;

import com.uitopic.rumbo.profiles.application.ports.ProfileImageStorage;
import com.uitopic.rumbo.profiles.domain.exceptions.ProfileNotFoundException;
import com.uitopic.rumbo.profiles.domain.model.aggregates.Profile;
import com.uitopic.rumbo.profiles.domain.model.commands.ReplaceProfileImageCommand;
import com.uitopic.rumbo.profiles.domain.repositories.ProfileRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReplaceProfileImageCommandHandler {
  private final ProfileRepository profiles;
  private final ProfileImageStorage storage;
  private final Clock clock;
  public ReplaceProfileImageCommandHandler(ProfileRepository profiles, ProfileImageStorage storage, Clock clock) {
    this.profiles = profiles;
    this.storage = storage;
    this.clock = clock;
  }
  @Transactional
  public Profile handle(ReplaceProfileImageCommand command) {
    java.util.Objects.requireNonNull(command.file(), "image is required");
    Profile profile = profiles.findByUserId(command.userId()).orElseThrow(ProfileNotFoundException::new);
    var uploaded = storage.store(command.userId(), command.file());
    try {
      ProfileImageCleanup.schedule(storage, profile.image() == null ? null : profile.image().storageKey(), uploaded.storageKey());
    } catch (RuntimeException error) {
      ProfileImageCleanup.deleteQuietly(storage, uploaded.storageKey());
      throw error;
    }
    return profiles.save(profile.replaceImage(uploaded, clock.instant()));
  }
}
