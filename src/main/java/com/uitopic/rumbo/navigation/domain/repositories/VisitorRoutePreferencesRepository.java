package com.uitopic.rumbo.navigation.domain.repositories;

import com.uitopic.rumbo.navigation.domain.model.valueobjects.RouteConstraints;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port to the accessibility preferences a visitor saved in Profiles and Preferences (TS-29, WI-TS29-02).
 * Navigation never reads the profile tables: an adapter in {@code navigation/infrastructure/acl} translates the
 * public facade of Profiles into {@link RouteConstraints}.
 * <p>Contract, implemented by {@code ProfilesVisitorRoutePreferencesRepository} over
 * {@code ProfilesContextFacade.getRoutePreferences(UUID)}:
 * <ul>
 *   <li>Empty when the user has no profile or saved nothing that restricts the route; never {@code null}.</li>
 *   <li>It may throw when Profiles is unavailable: the route is then calculated with the requested restrictions
 *   only, and the failure is logged.</li>
 * </ul>
 */
public interface VisitorRoutePreferencesRepository {
  Optional<RouteConstraints> findByVisitorId(UUID visitorId);
}
