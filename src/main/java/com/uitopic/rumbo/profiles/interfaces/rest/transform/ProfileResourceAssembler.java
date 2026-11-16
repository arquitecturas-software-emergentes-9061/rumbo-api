package com.uitopic.rumbo.profiles.interfaces.rest.transform;

import com.uitopic.rumbo.profiles.domain.model.aggregates.Profile;
import com.uitopic.rumbo.profiles.interfaces.rest.resources.ProfileResponse;

public final class ProfileResourceAssembler {
  private ProfileResourceAssembler() {}
  public static ProfileResponse toResponse(Profile profile) {
    return new ProfileResponse(profile.userId(), profile.fullName().value(), profile.role(),
        profile.phoneNumber() == null ? null : profile.phoneNumber().value(),
        profile.image() == null ? null : profile.image().url(), profile.preferences());
  }
}
