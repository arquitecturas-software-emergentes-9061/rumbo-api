package com.uitopic.rumbo.profiles.domain.model.commands;

import java.util.Objects;
import java.util.UUID;

public record UpdateRoutePreferencesCommand(UUID userId, boolean avoidStairs, boolean simplerRoute,
                                            boolean voiceGuidance) {
  public UpdateRoutePreferencesCommand { Objects.requireNonNull(userId, "userId is required"); }
}
