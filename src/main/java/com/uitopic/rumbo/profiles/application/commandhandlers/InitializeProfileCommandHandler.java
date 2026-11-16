package com.uitopic.rumbo.profiles.application.commandhandlers;

import com.uitopic.rumbo.profiles.domain.model.aggregates.Profile;
import com.uitopic.rumbo.profiles.domain.model.commands.InitializeProfileCommand;
import com.uitopic.rumbo.profiles.domain.repositories.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Creates the initial profile of an account (TS-33). It is idempotent: if the account already has a profile, nothing
 * changes, and {@code user_id UNIQUE} rejects a concurrent duplicate.
 */
@Service
public class InitializeProfileCommandHandler {
  private final ProfileRepository profiles;
  private final Clock clock;

  public InitializeProfileCommandHandler(ProfileRepository profiles, Clock clock) {
    this.profiles = profiles;
    this.clock = clock;
  }

  /**
   * Runs in its own transaction, so it commits independently of the registration that triggered it and its failures,
   * commit included, surface to the caller.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(InitializeProfileCommand command) {
    if (profiles.existsByUserId(command.userId())) return;
    profiles.save(Profile.initialize(command.userId(), command.fullName(), command.role(), clock.instant()));
  }
}
