package com.uitopic.rumbo.profiles.application.acl;

import com.uitopic.rumbo.profiles.domain.model.valueobjects.RoutePreferences;
import com.uitopic.rumbo.profiles.domain.repositories.ProfileRepository;
import com.uitopic.rumbo.profiles.interfaces.acl.ProfilesContextFacade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exports the saved route preferences; a profile without saved preferences answers the defaults. */
@Service
public class ProfilesContextFacadeImpl implements ProfilesContextFacade {
  private final ProfileRepository profiles;

  public ProfilesContextFacadeImpl(ProfileRepository profiles) {
    this.profiles = profiles;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<RoutePreferencesData> getRoutePreferences(UUID userId) {
    return profiles.findByUserId(userId).map(profile -> {
      RoutePreferences saved = profile.preferences() == null ? RoutePreferences.DEFAULTS : profile.preferences();
      return new RoutePreferencesData(saved.avoidStairs(), saved.simplerRoute(), saved.voiceGuidance());
    });
  }
}
