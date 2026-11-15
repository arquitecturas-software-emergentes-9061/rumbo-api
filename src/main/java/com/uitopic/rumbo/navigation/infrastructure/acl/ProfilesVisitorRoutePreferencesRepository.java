package com.uitopic.rumbo.navigation.infrastructure.acl;

import com.uitopic.rumbo.navigation.domain.model.valueobjects.RouteConstraints;
import com.uitopic.rumbo.navigation.domain.repositories.VisitorRoutePreferencesRepository;
import com.uitopic.rumbo.profiles.interfaces.acl.ProfilesContextFacade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Anticorruption layer from Profiles and Preferences to the route restrictions of Navigation (TS-29, WI-TS29-02).
 * <ul>
 *   <li>{@code avoidStairs} becomes {@link RouteConstraints#avoidStairs()}.</li>
 *   <li>{@code simplerRoute} and {@code voiceGuidance} change how the route is presented, not which connections it may
 *   use, so they do not restrict the calculation.</li>
 *   <li>Preferences that restrict nothing answer empty, so the requested restrictions are used as they are.</li>
 * </ul>
 */
@Component
public class ProfilesVisitorRoutePreferencesRepository implements VisitorRoutePreferencesRepository {
  private final ProfilesContextFacade profiles;

  public ProfilesVisitorRoutePreferencesRepository(ProfilesContextFacade profiles) {
    this.profiles = profiles;
  }

  @Override
  public Optional<RouteConstraints> findByVisitorId(UUID visitorId) {
    return profiles.getRoutePreferences(visitorId)
        .filter(ProfilesContextFacade.RoutePreferencesData::avoidStairs)
        .map(saved -> new RouteConstraints(true, false, false));
  }
}
