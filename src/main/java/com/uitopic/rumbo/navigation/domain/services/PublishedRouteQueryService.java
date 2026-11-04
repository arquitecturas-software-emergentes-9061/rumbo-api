package com.uitopic.rumbo.navigation.domain.services;

import com.uitopic.rumbo.navigation.domain.model.valueobjects.CalculatedRoute;
import java.util.Optional;
import java.util.UUID;

/** Query contract for calculating a route from one published site model. */
public interface PublishedRouteQueryService {
  /** Returns no route when the model or path is unavailable. */
  Optional<CalculatedRoute> calculate(UUID siteId, UUID fromNodeId, UUID toNodeId);
}
