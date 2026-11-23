package com.uitopic.rumbo.navigation.domain.services;

import com.uitopic.rumbo.navigation.domain.model.valueobjects.CalculatedRoute;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.RouteConstraints;
import java.util.Optional;
import java.util.UUID;

/** Query contract for calculating a route from one published site model. */
public interface PublishedRouteQueryService {
  /**
   * Loads the current published model for each calculation. For stateless recalculation (TS-22), callers supply
   * the updated origin together with their original destination and mobility constraints.
   * Returns empty when the model, node or path is unavailable; incompatible restrictions raise a domain error.
   */
  Optional<CalculatedRoute> calculate(UUID siteId, UUID fromNodeId, UUID toNodeId,
                                      RouteConstraints constraints);

  /** Keeps the former accessibility flag; equivalent to {@link RouteConstraints#accessibleOnly(boolean)}. */
  default Optional<CalculatedRoute> calculate(UUID siteId, UUID fromNodeId, UUID toNodeId,
                                              boolean accessibleOnly) {
    return calculate(siteId, fromNodeId, toNodeId, RouteConstraints.accessibleOnly(accessibleOnly));
  }

  /**
   * Calculates the route of an identified visitor: the requested restrictions are combined with the ones the visitor
   * saved in Profiles and Preferences, keeping the strictest of both (TS-29). Without a visitor or saved preferences
   * it is the same as {@link #calculate(UUID, UUID, UUID, RouteConstraints)}.
   */
  default Optional<CalculatedRoute> calculateForVisitor(UUID siteId, UUID fromNodeId, UUID toNodeId,
                                                        RouteConstraints requested, UUID visitorId) {
    return calculate(siteId, fromNodeId, toNodeId, requested);
  }

  /** Calculates without an accessibility preference. */
  default Optional<CalculatedRoute> calculate(UUID siteId, UUID fromNodeId, UUID toNodeId) {
    return calculate(siteId, fromNodeId, toNodeId, false);
  }
}
