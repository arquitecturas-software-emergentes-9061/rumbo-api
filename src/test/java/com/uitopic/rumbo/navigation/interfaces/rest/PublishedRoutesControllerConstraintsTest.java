package com.uitopic.rumbo.navigation.interfaces.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uitopic.rumbo.iam.domain.model.valueobjects.UserRole;
import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
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
    when(routes.calculateForVisitor(siteId, from, to, expected, null)).thenReturn(Optional.of(emptyRoute()));

    assertEquals(200, controller.find(siteId, from, to, true, true, true, null).getStatusCode().value());
    verify(routes).calculateForVisitor(siteId, from, to, expected, null);
  }

  @Test
  void withoutParametersNoRestrictionIsApplied() {
    when(routes.calculateForVisitor(siteId, from, to, RouteConstraints.NONE, null))
        .thenReturn(Optional.of(emptyRoute()));

    controller.find(siteId, from, to, false, false, false, null);

    verify(routes).calculateForVisitor(siteId, from, to, RouteConstraints.NONE, null);
  }

  @Test
  void noCompatibleRouteAnswersRouteNotFound() {
    when(routes.calculateForVisitor(eq(siteId), eq(from), eq(to), any(RouteConstraints.class), any()))
        .thenReturn(Optional.empty());

    assertThrows(RouteNotFoundException.class,
        () -> controller.find(siteId, from, to, false, true, false, null));
  }

  @Test
  void theCallerIsPassedAsTheVisitorWhoseSavedPreferencesApply() {
    UUID userId = UUID.randomUUID();
    var caller = new AuthenticatedPrincipal(userId, UserRole.PATIENT, UUID.randomUUID(), null);
    when(routes.calculateForVisitor(siteId, from, to, RouteConstraints.NONE, userId))
        .thenReturn(Optional.of(emptyRoute()));

    controller.find(siteId, from, to, false, false, false, caller);

    verify(routes).calculateForVisitor(siteId, from, to, RouteConstraints.NONE, userId);
  }

  private CalculatedRoute emptyRoute() {
    var origin = new PublishedSpatialGraph.Node(from, UUID.randomUUID(), UUID.randomUUID(), 0, 0,
        PublishedSpatialGraph.NodeRole.ACCESS);
    return new CalculatedRoute(siteId, UUID.randomUUID(), List.of(origin), List.of(), 0, 0);
  }
}
