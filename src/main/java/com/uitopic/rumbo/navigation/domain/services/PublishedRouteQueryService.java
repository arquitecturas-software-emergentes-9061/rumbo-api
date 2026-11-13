package com.uitopic.rumbo.navigation.domain.services;

import com.uitopic.rumbo.navigation.domain.model.valueobjects.CalculatedRoute;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.RouteConstraints;
import java.util.Optional;
import java.util.UUID;

/** Query contract for calculating a route from one published site model. */
public interface PublishedRouteQueryService {
  /** Returns no route when the model or path is unavailable, or when no path satisfies the constraints. */
  Optional<CalculatedRoute> calculate(UUID siteId, UUID fromNodeId, UUID toNodeId,
                                      RouteConstraints constraints);

  /** Keeps the former accessibility flag; equivalent to {@link RouteConstraints#accessibleOnly(boolean)}. */
  default Optional<CalculatedRoute> calculate(UUID siteId, UUID fromNodeId, UUID toNodeId,
                                              boolean accessibleOnly) {
    return calculate(siteId, fromNodeId, toNodeId, RouteConstraints.accessibleOnly(accessibleOnly));
  }

  /** Calculates without an accessibility preference. */
  default Optional<CalculatedRoute> calculate(UUID siteId, UUID fromNodeId, UUID toNodeId) {
    return calculate(siteId, fromNodeId, toNodeId, false);
  }
}
