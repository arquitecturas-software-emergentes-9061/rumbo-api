package com.uitopic.rumbo.profiles.interfaces.rest.resources;

import com.uitopic.rumbo.profiles.domain.model.aggregates.Profile;

public record RoutePreferencesResponse(boolean avoidStairs, boolean simplerRoute, boolean voiceGuidance,
                                       boolean configured) {
  public static RoutePreferencesResponse from(Profile profile) {
    return new RoutePreferencesResponse(profile.preferences().avoidStairs(), profile.preferences().simplerRoute(),
        profile.preferences().voiceGuidance(), profile.preferencesUpdatedAt() != null);
  }
}
