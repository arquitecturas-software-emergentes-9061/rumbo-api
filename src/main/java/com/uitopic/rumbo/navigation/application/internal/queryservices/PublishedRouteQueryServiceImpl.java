package com.uitopic.rumbo.navigation.application.internal.queryservices;

import com.uitopic.rumbo.navigation.domain.services.AStarRouteCalculationDomainService;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.CalculatedRoute;
import com.uitopic.rumbo.navigation.domain.repositories.PublishedSpatialGraphRepository;
import com.uitopic.rumbo.navigation.domain.services.PublishedRouteQueryService;
import com.uitopic.rumbo.navigation.domain.exceptions.InvalidPublishedGraphException;
import com.uitopic.rumbo.navigation.domain.exceptions.NoCompatibleRouteException;
import com.uitopic.rumbo.navigation.domain.exceptions.RouteNodeOutsideSiteException;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedSpatialGraph;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves one published graph before asking the domain calculator for a path. */
@Service
@Profile("postgres")
public class PublishedRouteQueryServiceImpl implements PublishedRouteQueryService {
  private final PublishedSpatialGraphRepository graphs;
  private final AStarRouteCalculationDomainService calculator;

  public PublishedRouteQueryServiceImpl(PublishedSpatialGraphRepository graphs,
                                        AStarRouteCalculationDomainService calculator) {
    this.graphs = graphs;
    this.calculator = calculator;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CalculatedRoute> calculate(UUID siteId, UUID fromNodeId, UUID toNodeId,
                                             boolean accessibleOnly) {
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
      Optional<CalculatedRoute> route =
          calculator.calculate(graph, fromNodeId, toNodeId, accessibleOnly);
      if (accessibleOnly && route.isEmpty()
          && calculator.calculate(graph, fromNodeId, toNodeId, false).isPresent()) {
        throw new NoCompatibleRouteException();
      }
      return route;
    } catch (IllegalArgumentException | IllegalStateException error) {
      throw new InvalidPublishedGraphException(error);
    }
  }
}
