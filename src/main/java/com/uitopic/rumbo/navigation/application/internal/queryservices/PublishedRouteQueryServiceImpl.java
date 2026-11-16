package com.uitopic.rumbo.navigation.application.internal.queryservices;

import com.uitopic.rumbo.navigation.domain.services.AStarRouteCalculationDomainService;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.CalculatedRoute;
import com.uitopic.rumbo.navigation.domain.repositories.PublishedSpatialGraphRepository;
import com.uitopic.rumbo.navigation.domain.repositories.VisitorRoutePreferencesRepository;
import com.uitopic.rumbo.navigation.domain.services.PublishedRouteQueryService;
import com.uitopic.rumbo.navigation.domain.exceptions.InvalidPublishedGraphException;
import com.uitopic.rumbo.navigation.domain.exceptions.NoCompatibleRouteException;
import com.uitopic.rumbo.navigation.domain.exceptions.RouteNodeOutsideSiteException;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedSpatialGraph;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.RouteConstraints;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves one published graph before asking the domain calculator for a path. */
@Service
@Profile("postgres")
public class PublishedRouteQueryServiceImpl implements PublishedRouteQueryService {
  private static final Logger log = LoggerFactory.getLogger(PublishedRouteQueryServiceImpl.class);
  private final PublishedSpatialGraphRepository graphs;
  private final AStarRouteCalculationDomainService calculator;
  private final ObjectProvider<VisitorRoutePreferencesRepository> preferences;

  public PublishedRouteQueryServiceImpl(PublishedSpatialGraphRepository graphs,
                                        AStarRouteCalculationDomainService calculator,
                                        ObjectProvider<VisitorRoutePreferencesRepository> preferences) {
    this.graphs = graphs;
    this.calculator = calculator;
    this.preferences = preferences;
  }

  /**
   * Adds the visitor's saved restrictions to the requested ones. The preferences port is looked up lazily, so routes
   * keep working before Profiles and Preferences exists, and a failure there never blocks a route.
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<CalculatedRoute> calculateForVisitor(UUID siteId, UUID fromNodeId, UUID toNodeId,
                                                       RouteConstraints requested, UUID visitorId) {
    RouteConstraints effective = requested;
    VisitorRoutePreferencesRepository saved = visitorId == null ? null : preferences.getIfAvailable();
    if (saved != null) {
      try {
        effective = saved.findByVisitorId(visitorId).map(requested::and).orElse(requested);
      } catch (RuntimeException unavailable) {
        log.warn("Saved route preferences of visitor {} unavailable; using the requested ones", visitorId,
            unavailable);
      }
    }
    return calculate(siteId, fromNodeId, toNodeId, effective);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CalculatedRoute> calculate(UUID siteId, UUID fromNodeId, UUID toNodeId,
                                             RouteConstraints constraints) {
    PublishedSpatialGraph graph;
    try {
      graph = graphs.findCurrentBySite(siteId).orElse(null);
    } catch (IllegalArgumentException | IllegalStateException error) {
      throw new InvalidPublishedGraphException(error);
    }
    if (graph == null) return Optional.empty();
    if (!graph.siteId().equals(siteId)) {
      throw new InvalidPublishedGraphException(
          new IllegalArgumentException("Published graph belongs to another site"));
    }
    Set<UUID> nodeIds = graph.nodes().stream()
        .map(PublishedSpatialGraph.Node::id).collect(Collectors.toSet());
    if (!nodeIds.contains(fromNodeId) || !nodeIds.contains(toNodeId)) {
      if ((!nodeIds.contains(fromNodeId)
          && graphs.existsCurrentNodeOutsideSite(fromNodeId, siteId))
          || (!nodeIds.contains(toNodeId)
          && graphs.existsCurrentNodeOutsideSite(toNodeId, siteId))) {
        throw new RouteNodeOutsideSiteException();
      }
      return Optional.empty();
    }
    try {
      Optional<CalculatedRoute> route = calculator.calculate(graph, fromNodeId, toNodeId, constraints);
      // A route exists but none satisfies the restrictions: the visitor must know it is the preference that blocks it.
      if (route.isEmpty() && !RouteConstraints.NONE.equals(constraints)
          && calculator.calculate(graph, fromNodeId, toNodeId, RouteConstraints.NONE).isPresent()) {
        throw new NoCompatibleRouteException();
      }
      return route;
    } catch (IllegalArgumentException | IllegalStateException error) {
      throw new InvalidPublishedGraphException(error);
    }
  }
}
