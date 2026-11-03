package com.uitopic.rumbo.navigation.application.internal.queryservices;

import com.uitopic.rumbo.navigation.domain.AStarRouteCalculationDomainService;
import com.uitopic.rumbo.navigation.domain.CalculatedRoute;
import com.uitopic.rumbo.navigation.domain.PublishedSpatialGraphRepository;
import com.uitopic.rumbo.navigation.domain.services.PublishedRouteQueryService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

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
  public Optional<CalculatedRoute> calculate(UUID siteId, UUID fromNodeId, UUID toNodeId) {
    return graphs.findCurrentBySite(siteId)
        .flatMap(graph -> calculator.calculate(graph, fromNodeId, toNodeId));
  }
}
