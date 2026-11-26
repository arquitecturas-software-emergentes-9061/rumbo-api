package com.uitopic.rumbo.profiles.interfaces.acl;

import java.util.Optional;
import java.util.UUID;

/**
 * Stable read-only Profiles and Preferences contract for other bounded contexts (TS-29, WI-TS29-02). Navigation uses
 * it to apply the route preferences a visitor saved, without depending on the profile aggregate or its tables.
 */
public interface ProfilesContextFacade {
  /** Saved route preferences of an account; empty when the account has no profile. */
  Optional<RoutePreferencesData> getRoutePreferences(UUID userId);

  /**
   * Route preferences exported by Profiles and Preferences.
   *
   * @param avoidStairs the visitor never wants stairs in the route
   * @param simplerRoute the visitor prefers fewer turns and decisions
   * @param voiceGuidance the visitor wants spoken instructions
   */
  record RoutePreferencesData(boolean avoidStairs, boolean simplerRoute, boolean voiceGuidance) {}
}
