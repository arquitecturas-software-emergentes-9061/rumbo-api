package com.uitopic.rumbo.profiles.application.commandhandlers;

import com.uitopic.rumbo.profiles.domain.exceptions.ProfileNotFoundException;
import com.uitopic.rumbo.profiles.domain.model.aggregates.Profile;
import com.uitopic.rumbo.profiles.domain.model.commands.UpdateProfileCommand;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.FullName;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.PhoneNumber;
import com.uitopic.rumbo.profiles.domain.repositories.ProfileRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateProfileCommandHandler {
  private final ProfileRepository profiles;
  private final Clock clock;
  public UpdateProfileCommandHandler(ProfileRepository profiles, Clock clock) {
    this.profiles = profiles;
    this.clock = clock;
  }
  @Transactional
  public Profile handle(UpdateProfileCommand command) {
    FullName name = new FullName(command.fullName());
    PhoneNumber phone = command.phoneNumber() == null || command.phoneNumber().isBlank()
        ? null : new PhoneNumber(command.phoneNumber());
    Profile profile = profiles.findByUserId(command.userId()).orElseThrow(ProfileNotFoundException::new);
    return profiles.save(profile.updatePersonalData(name, phone, clock.instant()));
  }
}
