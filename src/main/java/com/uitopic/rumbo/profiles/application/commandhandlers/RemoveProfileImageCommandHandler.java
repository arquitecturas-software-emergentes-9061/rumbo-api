package com.uitopic.rumbo.profiles.application.commandhandlers;

import com.uitopic.rumbo.profiles.application.ports.ProfileImageStorage;
import com.uitopic.rumbo.profiles.domain.exceptions.ProfileNotFoundException;
import com.uitopic.rumbo.profiles.domain.model.aggregates.Profile;
import com.uitopic.rumbo.profiles.domain.model.commands.RemoveProfileImageCommand;
import com.uitopic.rumbo.profiles.domain.repositories.ProfileRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RemoveProfileImageCommandHandler {
  private final ProfileRepository profiles;
  private final ProfileImageStorage storage;
  private final Clock clock;
  public RemoveProfileImageCommandHandler(ProfileRepository profiles, ProfileImageStorage storage, Clock clock) {
    this.profiles = profiles;
    this.storage = storage;
    this.clock = clock;
  }
  @Transactional
  public Profile handle(RemoveProfileImageCommand command) {
    Profile profile = profiles.findByUserId(command.userId()).orElseThrow(ProfileNotFoundException::new);
    storage.requireAvailable();
    if (profile.image() == null) return profile;
    ProfileImageCleanup.schedule(storage, profile.image().storageKey(), null);
    return profiles.save(profile.removeImage(clock.instant()));
  }
}
