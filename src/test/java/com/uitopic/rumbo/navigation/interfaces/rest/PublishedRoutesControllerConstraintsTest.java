package com.uitopic.rumbo.navigation.interfaces.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uitopic.rumbo.navigation.application.internal.exceptions.RouteNotFoundException;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.CalculatedRoute;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedSpatialGraph;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.RouteConstraints;
import com.uitopic.rumbo.navigation.domain.services.PublishedRouteQueryService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** TS-29: the route endpoint turns its query parameters into {@link RouteConstraints}. */
class PublishedRoutesControllerConstraintsTest {
  private final PublishedRouteQueryService routes = mock(PublishedRouteQueryService.class);
  private final PublishedRoutesController controller = new PublishedRoutesController(routes);
  private final UUID siteId = UUID.randomUUID();
  private final UUID from = UUID.randomUUID();
  private final UUID to = UUID.randomUUID();

  @Test
  void passesEveryRestrictionToTheQueryService() {
    var expected = new RouteConstraints(true, true, true);
    when(routes.calculate(siteId, from, to, expected)).thenReturn(Optional.of(emptyRoute()));

    assertEquals(200, controller.find(siteId, from, to, true, true, true).getStatusCode().value());
    verify(routes).calculate(siteId, from, to, expected);
  }

  @Test
  void withoutParametersNoRestrictionIsApplied() {
    when(routes.calculate(siteId, from, to, RouteConstraints.NONE)).thenReturn(Optional.of(emptyRoute()));

    controller.find(siteId, from, to, false, false, false);

    verify(routes).calculate(siteId, from, to, RouteConstraints.NONE);
  }

  @Test
  void noCompatibleRouteAnswersRouteNotFound() {
    when(routes.calculate(eq(siteId), eq(from), eq(to), any(RouteConstraints.class))).thenReturn(Optional.empty());

    assertThrows(RouteNotFoundException.class, () -> controller.find(siteId, from, to, false, true, false));
  }

  private CalculatedRoute emptyRoute() {
    var origin = new PublishedSpatialGraph.Node(from, UUID.randomUUID(), UUID.randomUUID(), 0, 0,
        PublishedSpatialGraph.NodeRole.ACCESS);
    return new CalculatedRoute(siteId, UUID.randomUUID(), List.of(origin), List.of(), 0, 0);
  }
}
