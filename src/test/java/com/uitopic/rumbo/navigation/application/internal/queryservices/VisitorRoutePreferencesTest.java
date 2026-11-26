package com.uitopic.rumbo.navigation.application.internal.queryservices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uitopic.rumbo.navigation.domain.exceptions.NoCompatibleRouteException;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.CalculatedRoute;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedSpatialGraph;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedSpatialGraph.Accessibility;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedSpatialGraph.LinkKind;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.RouteConstraints;
import com.uitopic.rumbo.navigation.domain.repositories.PublishedSpatialGraphRepository;
import com.uitopic.rumbo.navigation.domain.repositories.VisitorRoutePreferencesRepository;
import com.uitopic.rumbo.navigation.domain.services.AStarRouteCalculationDomainService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** TS-29 (WI-TS29-02 and WI-TS29-05): saved accessibility preferences reach the route calculation. */
class VisitorRoutePreferencesTest {
  private final UUID siteId = UUID.randomUUID();
  private final UUID visitorId = UUID.randomUUID();
  private final UUID lowerFloor = UUID.randomUUID();
  private final UUID upperFloor = UUID.randomUUID();
  private final UUID lowerPlan = UUID.randomUUID();
  private final UUID upperPlan = UUID.randomUUID();
  private final PublishedSpatialGraph.Node lobby = node(lowerFloor, lowerPlan);
  private final PublishedSpatialGraph.Node cardiology = node(upperFloor, upperPlan);
  private final PublishedSpatialGraph.Link stairs = link(LinkKind.STAIRS, 1, Accessibility.INACCESSIBLE);
  private final PublishedSpatialGraph.Link elevator = link(LinkKind.ELEVATOR, 5, Accessibility.ACCESSIBLE);
  private final PublishedSpatialGraphRepository graphs = mock(PublishedSpatialGraphRepository.class);
  private final VisitorRoutePreferencesRepository saved = mock(VisitorRoutePreferencesRepository.class);

  @Test
  void savedPreferenceToAvoidStairsChangesTheRoute() {
    when(saved.findByVisitorId(visitorId)).thenReturn(Optional.of(new RouteConstraints(true, false, false)));

    CalculatedRoute route = service(saved).calculateForVisitor(siteId, lobby.id(), cardiology.id(),
        RouteConstraints.NONE, visitorId).orElseThrow();

    assertEquals(elevator.id(), route.legs().getFirst().linkId());
  }

  @Test
  void requestedRestrictionsAreKeptWhenTheVisitorSavedNone() {
    when(saved.findByVisitorId(visitorId)).thenReturn(Optional.empty());

    CalculatedRoute route = service(saved).calculateForVisitor(siteId, lobby.id(), cardiology.id(),
        new RouteConstraints(false, true, false), visitorId).orElseThrow();

    assertEquals(elevator.id(), route.legs().getFirst().linkId());
  }

  @Test
  void savedPreferencesCannotRelaxWhatTheRequestAsked() {
    when(saved.findByVisitorId(visitorId)).thenReturn(Optional.of(RouteConstraints.NONE));

    CalculatedRoute route = service(saved).calculateForVisitor(siteId, lobby.id(), cardiology.id(),
        new RouteConstraints(true, false, false), visitorId).orElseThrow();

    assertEquals(elevator.id(), route.legs().getFirst().linkId());
  }

  @Test
  void withoutProfilesTheRequestedRestrictionsAloneApply() {
    CalculatedRoute route = service(null).calculateForVisitor(siteId, lobby.id(), cardiology.id(),
        RouteConstraints.NONE, visitorId).orElseThrow();

    assertEquals(stairs.id(), route.legs().getFirst().linkId());
  }

  @Test
  void anUnavailableProfilesContextNeverBlocksTheRoute() {
    when(saved.findByVisitorId(visitorId)).thenThrow(new IllegalStateException("profiles down"));

    CalculatedRoute route = service(saved).calculateForVisitor(siteId, lobby.id(), cardiology.id(),
        RouteConstraints.NONE, visitorId).orElseThrow();

    assertEquals(stairs.id(), route.legs().getFirst().linkId());
  }

  @Test
  void anAnonymousCallerIsNeverLookedUp() {
    service(saved).calculateForVisitor(siteId, lobby.id(), cardiology.id(), RouteConstraints.NONE, null);

    verify(saved, never()).findByVisitorId(visitorId);
  }

  @Test
  void savedPreferencesThatNoConnectionSatisfiesAreReportedAsIncompatible() {
    when(saved.findByVisitorId(visitorId)).thenReturn(Optional.of(new RouteConstraints(true, false, false)));
    var onlyStairs = new PublishedSpatialGraph(siteId, UUID.randomUUID(), UUID.randomUUID(), plans(),
        List.of(lobby, cardiology), List.of(stairs));
    when(graphs.findCurrentBySite(siteId)).thenReturn(Optional.of(onlyStairs));

    assertThrows(NoCompatibleRouteException.class, () -> service(saved, false).calculateForVisitor(siteId,
        lobby.id(), cardiology.id(), RouteConstraints.NONE, visitorId));
  }

  private PublishedRouteQueryServiceImpl service(VisitorRoutePreferencesRepository repository) {
    return service(repository, true);
  }

  private PublishedRouteQueryServiceImpl service(VisitorRoutePreferencesRepository repository, boolean bothLinks) {
    if (bothLinks) {
      when(graphs.findCurrentBySite(siteId)).thenReturn(Optional.of(new PublishedSpatialGraph(siteId,
          UUID.randomUUID(), UUID.randomUUID(), plans(), List.of(lobby, cardiology), List.of(stairs, elevator))));
    }
    StaticListableBeanFactory factory = new StaticListableBeanFactory();
    if (repository != null) factory.addBean("preferences", repository);
    ObjectProvider<VisitorRoutePreferencesRepository> provider =
        factory.getBeanProvider(VisitorRoutePreferencesRepository.class);
    return new PublishedRouteQueryServiceImpl(graphs, new AStarRouteCalculationDomainService(), provider);
  }

  private List<PublishedSpatialGraph.Plan> plans() {
    return List.of(new PublishedSpatialGraph.Plan(lowerFloor, lowerPlan, 100, 100, 1),
        new PublishedSpatialGraph.Plan(upperFloor, upperPlan, 100, 100, 1));
  }

  private static PublishedSpatialGraph.Node node(UUID floor, UUID plan) {
    return new PublishedSpatialGraph.Node(UUID.randomUUID(), floor, plan, 1, 1,
        PublishedSpatialGraph.NodeRole.VERTICAL_LINK);
  }

  private PublishedSpatialGraph.Link link(LinkKind kind, double weight, Accessibility accessibility) {
    return new PublishedSpatialGraph.Link(UUID.randomUUID(), lobby.id(), cardiology.id(), kind, 5, weight, 0,
        accessibility, true);
  }
}
