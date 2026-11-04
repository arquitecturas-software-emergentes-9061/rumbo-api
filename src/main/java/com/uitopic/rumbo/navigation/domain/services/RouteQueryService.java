package com.uitopic.rumbo.navigation.domain.services;

import com.uitopic.rumbo.navigation.domain.model.queries.CalculateRouteQuery;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.RoutePath;

/** Calculates a route from a navigable model for the provisional API. */
public interface RouteQueryService {
  RoutePath calculate(CalculateRouteQuery query);
}
