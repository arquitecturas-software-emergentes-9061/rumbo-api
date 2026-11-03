package com.uitopic.rumbo.navigation.infrastructure.acl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.uitopic.rumbo.navigation.application.GetPublishedSpatialGraphQueryHandler;
import com.uitopic.rumbo.navigation.domain.PublishedSpatialGraph;
import com.uitopic.rumbo.navigation.domain.CalculatedRoute;
import com.uitopic.rumbo.navigation.domain.services.PublishedRouteQueryService;
import com.uitopic.rumbo.planning.domain.repositories.IndoorMapRepository;
import com.uitopic.rumbo.planning.domain.exceptions.InvalidMapGraphException;
import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import com.uitopic.rumbo.planning.domain.model.commands.PublishMapVersionCommand;
import com.uitopic.rumbo.planning.domain.model.entities.MapConnection;
import com.uitopic.rumbo.planning.domain.model.entities.MapNode;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphIssueCode;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapScale;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PlanCoordinateSpace;
import com.uitopic.rumbo.planning.domain.model.valueobjects.SpatialCoordinate;
import com.uitopic.rumbo.planning.domain.services.IndoorMapCommandService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** PostgreSQL verification of the published Planning to Navigation read boundary. */
@SpringBootTest
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "RUMBO_TEST_DB_URL", matches = ".+")
class PlanningPublishedSpatialGraphRepositoryIntegrationTest {
  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", () -> System.getenv("RUMBO_TEST_DB_URL"));
    properties.add("spring.datasource.username", () -> System.getenv("RUMBO_TEST_DB_USER"));
    properties.add("spring.datasource.password", () -> System.getenv("RUMBO_TEST_DB_PASSWORD"));
  }

  @Autowired IndoorMapRepository store;
  @Autowired IndoorMapCommandService commands;
  @Autowired GetPublishedSpatialGraphQueryHandler navigation;
  @Autowired PublishedRouteQueryService routes;

  @Test
  void readsOnlyCurrentPublishedVersionWithVerticalWeightAndScale() {
    UUID siteId = UUID.randomUUID();
    UUID mapId = UUID.randomUUID();
    UUID lowerFloor = UUID.randomUUID();
    UUID upperFloor = UUID.randomUUID();
    IndoorMap first = twoFloorDraft(siteId, mapId, UUID.randomUUID(), lowerFloor, upperFloor, 7);
    store.saveDraft(first);

    assertTrue(navigation.handle(siteId).isEmpty());
    assertTrue(routes.calculate(siteId, first.nodes().getFirst().id(),
        first.nodes().getLast().id()).isEmpty());
    commands.handle(new PublishMapVersionCommand(siteId, mapId, first.modelVersionId()));
    PublishedSpatialGraph published = navigation.handle(siteId).orElseThrow();
    assertEquals(first.modelVersionId(), published.modelVersionId());
    assertEquals(siteId, published.siteId());
    assertEquals(2, published.plans().size());
    assertEquals(0.25, published.plans().getFirst().metersPerUnit());
    assertEquals(2, published.nodes().size());
    assertEquals(PublishedSpatialGraph.LinkKind.ELEVATOR, published.links().getFirst().kind());
    assertEquals(7, published.links().getFirst().weight());
    assertEquals(3, published.links().getFirst().verticalPenalty());
    CalculatedRoute route = routes.calculate(siteId, first.nodes().getFirst().id(),
        first.nodes().getLast().id()).orElseThrow();
    assertEquals(10, route.totalCost());
    assertEquals(5, route.totalDistanceMeters());
    assertEquals(first.modelVersionId(), route.modelVersionId());
    assertFalse(navigation.handle(UUID.randomUUID()).isPresent());

    IndoorMap next = twoFloorDraft(siteId, mapId, UUID.randomUUID(), lowerFloor, upperFloor, 11);
    store.saveDraft(next);
    assertEquals(first.modelVersionId(), navigation.handle(siteId).orElseThrow().modelVersionId());
    commands.handle(new PublishMapVersionCommand(siteId, mapId, next.modelVersionId()));
    PublishedSpatialGraph current = navigation.handle(siteId).orElseThrow();
    assertEquals(next.modelVersionId(), current.modelVersionId());
    assertEquals(11, current.links().getFirst().weight());
    assertEquals(3, current.links().getFirst().verticalPenalty());
    assertEquals(14, routes.calculate(siteId, next.nodes().getFirst().id(),
        next.nodes().getLast().id()).orElseThrow().totalCost());
  }

  @Test
  void rejectsFloorAlreadyOwnedByAnotherSite() {
    UUID ownedFloor = UUID.randomUUID();
    IndoorMap first = twoFloorDraft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        ownedFloor, UUID.randomUUID(), 7);
    store.saveDraft(first);
    IndoorMap foreign = twoFloorDraft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        ownedFloor, UUID.randomUUID(), 7);

    InvalidMapGraphException error = assertThrows(InvalidMapGraphException.class,
        () -> store.saveDraft(foreign));
    assertEquals(GraphIssueCode.PLAN_FLOOR_MISMATCH, error.issue().code());
  }

  @Test
  void rejectsSecondIndoorMapForTheSameSite() {
    UUID siteId = UUID.randomUUID();
    IndoorMap first = twoFloorDraft(siteId, UUID.randomUUID(), UUID.randomUUID(),
        UUID.randomUUID(), UUID.randomUUID(), 7);
    store.saveDraft(first);
    IndoorMap second = twoFloorDraft(siteId, UUID.randomUUID(), UUID.randomUUID(),
        UUID.randomUUID(), UUID.randomUUID(), 7);

    assertThrows(DataIntegrityViolationException.class, () -> store.saveDraft(second));
  }

  private IndoorMap twoFloorDraft(UUID siteId, UUID mapId, UUID versionId,
                                  UUID lowerFloor, UUID upperFloor, double weight) {
    IndoorMap map = new IndoorMap(mapId, siteId, versionId);
    PlanCoordinateSpace lower = new PlanCoordinateSpace(lowerFloor, UUID.randomUUID(), 100, 100);
    PlanCoordinateSpace upper = new PlanCoordinateSpace(upperFloor, UUID.randomUUID(), 100, 100);
    map.addPlan(lower);
    map.addPlan(upper);
    map.setScale(new MapScale(lowerFloor, lower.planVersionId(), 0.25));
    map.setScale(new MapScale(upperFloor, upper.planVersionId(), 0.25));
    MapNode access = new MapNode(UUID.randomUUID(), versionId,
        new SpatialCoordinate(lowerFloor, lower.planVersionId(), 2, 2), NodeType.ACCESS, lower);
    MapNode destination = new MapNode(UUID.randomUUID(), versionId,
        new SpatialCoordinate(upperFloor, upper.planVersionId(), 3, 3), NodeType.DESTINATION, upper);
    map.addNode(access);
    map.addNode(destination);
    map.addConnection(new MapConnection(UUID.randomUUID(), versionId, access.id(),
        destination.id(), ConnectionType.ELEVATOR, 5, weight, 3, true));
    return map;
  }
}
