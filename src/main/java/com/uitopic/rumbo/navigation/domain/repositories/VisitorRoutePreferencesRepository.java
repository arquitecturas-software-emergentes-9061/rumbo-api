package com.uitopic.rumbo.navigation.domain.repositories;

import com.uitopic.rumbo.navigation.domain.model.valueobjects.RouteConstraints;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port to the accessibility preferences a visitor saved in Profiles and Preferences (TS-29, WI-TS29-02).
 * Navigation never reads the profile tables: an adapter in {@code navigation/infrastructure/acl} translates the
 * public facade of Profiles into {@link RouteConstraints}.
 * <p>Contract for the adapter, so the route query works unchanged when it arrives:
 * <ul>
 *   <li>Expected facade: {@code ProfilesContextFacade.getNavigationPreferences(UUID userId)} returning the three
 *   flags {@code avoidStairs}, {@code requiresAccessibleRoute} and {@code prioritizeElevators}.</li>
 *   <li>Empty when the user has no profile or saved no preference; never {@code null}.</li>
 *   <li>It may throw when Profiles is unavailable: the route is then calculated with the requested restrictions
 *   only, and the failure is logged.</li>
 * </ul>
 * Until an implementation is registered, routes use only the restrictions sent in the request.
 */
public interface VisitorRoutePreferencesRepository {
  Optional<RouteConstraints> findByVisitorId(UUID visitorId);
}
