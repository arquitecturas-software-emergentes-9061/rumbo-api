package com.uitopic.rumbo.navigation.application.internal.queryservices;
import com.uitopic.rumbo.navigation.domain.exceptions.NavigationValidationException;
import com.uitopic.rumbo.navigation.domain.model.queries.CalculateRouteQuery;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.NodeKey;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.RoutePath;
import com.uitopic.rumbo.navigation.domain.repositories.NavigableModelRepository;
import com.uitopic.rumbo.navigation.domain.services.RouteCalculationDomainService;
import com.uitopic.rumbo.navigation.domain.services.RouteQueryService;

import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Resolves a provisional route query against the demo read model. */
@Service
@Profile({"demo & !postgres", "postgres & local"})
public final class DemoRouteQueryServiceImpl implements RouteQueryService {
  private final NavigableModelRepository repository;
  private final RouteCalculationDomainService planner;
  public DemoRouteQueryServiceImpl(NavigableModelRepository repository,RouteCalculationDomainService planner) {
    this.repository=Objects.requireNonNull(repository);this.planner=Objects.requireNonNull(planner);
  }
  @Override
  public RoutePath calculate(CalculateRouteQuery c) {
    var model=repository.publishedNetwork(c.siteId()).orElseThrow(()->new NavigationValidationException("No hay mapa publicado para esta sede."));
    if (!model.siteId().equals(c.siteId())) throw new NavigationValidationException("Modelo publicado no pertenece a esta sede.");
    return planner.calculate(model,new NodeKey(c.startFloorId(),c.startNodeId()),
      new NodeKey(c.destinationFloorId(),c.destinationNodeId()),c.accessibleOnly());
  }
}
