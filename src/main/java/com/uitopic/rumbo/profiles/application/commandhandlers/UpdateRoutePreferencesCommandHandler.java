package com.uitopic.rumbo.profiles.application.commandhandlers;

import com.uitopic.rumbo.profiles.domain.exceptions.ProfileNotFoundException;
import com.uitopic.rumbo.profiles.domain.model.aggregates.Profile;
import com.uitopic.rumbo.profiles.domain.model.commands.UpdateRoutePreferencesCommand;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.RoutePreferences;
import com.uitopic.rumbo.profiles.domain.repositories.ProfileRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateRoutePreferencesCommandHandler {
  private final ProfileRepository profiles;
  private final Clock clock;

  public UpdateRoutePreferencesCommandHandler(ProfileRepository profiles, Clock clock) {
    this.profiles = profiles;
    this.clock = clock;
  }

  @Transactional
  public Profile handle(UpdateRoutePreferencesCommand command) {
    Profile profile = profiles.findByUserId(command.userId()).orElseThrow(ProfileNotFoundException::new);
    return profiles.save(profile.updatePreferences(new RoutePreferences(command.avoidStairs(),
        command.simplerRoute(), command.voiceGuidance()), clock.instant()));
  }
}
