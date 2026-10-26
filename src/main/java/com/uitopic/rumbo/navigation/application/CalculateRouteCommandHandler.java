package com.uitopic.rumbo.navigation.application;
import com.uitopic.rumbo.navigation.domain.*;
import java.util.Objects;
public final class CalculateRouteCommandHandler {
  private final NavigableModelRepository repository;
  private final RouteCalculationDomainService planner;
  public CalculateRouteCommandHandler(NavigableModelRepository repository,RouteCalculationDomainService planner) {
    this.repository=Objects.requireNonNull(repository);this.planner=Objects.requireNonNull(planner);
  }
  public RoutePath handle(CalculateRouteCommand c) {
    var model=repository.publishedNetwork(c.siteId()).orElseThrow(()->new NavigationValidationException("No hay mapa publicado para esta sede."));
    if (!model.siteId().equals(c.siteId())) throw new NavigationValidationException("Modelo publicado no pertenece a esta sede.");
    return planner.calculate(model,new NodeKey(c.startFloorId(),c.startNodeId()),
      new NodeKey(c.destinationFloorId(),c.destinationNodeId()),c.accessibleOnly());
  }
}
