package com.uitopic.rumbo.navigation.application.internal.queryservices;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.navigation.application.internal.queryservices.BlePositionQueryService.*;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedSpatialGraph;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedSpatialGraph.*;
import com.uitopic.rumbo.navigation.domain.services.PublishedSpatialGraphQueryService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

class BlePositionQueryServiceTest {
  private final UUID site = UUID.randomUUID(), version = UUID.randomUUID(),
      floor = UUID.randomUUID(), plan = UUID.randomUUID(), first = UUID.randomUUID(),
      second = UUID.randomUUID(), beaconA = UUID.randomUUID(), beaconB = UUID.randomUUID();
  private final Instant now = Instant.parse("2026-11-25T12:00:00Z");
  private final PublishedSpatialGraphQueryService graphs = mock(PublishedSpatialGraphQueryService.class);
  private BlePositionQueryService service;

  @TempDir java.nio.file.Path directory;

  @BeforeEach void configureSurvey() throws Exception {
    var mapper = new ObjectMapper();
    var file = directory.resolve("survey.json");
    mapper.writeValue(file.toFile(), List.of(new Survey(site, version, List.of(
        new Point(first, List.of(new Baseline(beaconA, 1, 1, -51), new Baseline(beaconB, 1, 1, -82))),
        new Point(second, List.of(new Baseline(beaconA, 1, 1, -83), new Baseline(beaconB, 1, 1, -52)))))));
    when(graphs.findCurrentBySite(site)).thenReturn(Optional.of(graph(version)));
    service = new BlePositionQueryService(graphs, mapper, new DefaultResourceLoader(), file.toUri().toString());
  }

  @Test void returnsPublishedCoordinatesAndScaleForMeasuredReference() {
    var fix = service.resolve(site, signals(-50, -81, now), now);
    assertNotNull(fix);
    assertEquals(first, fix.nodeId());
    assertEquals(version, fix.modelVersionId());
    assertEquals(plan, fix.planVersionId());
    assertEquals(1, fix.x());
    assertEquals(.05, fix.metersPerUnit());
    assertEquals(now, fix.observedAt());
    assertEquals(2, service.beacons(site).size());
    assertEquals(second, service.resolve(site, signals(-82, -52, now), now).nodeId());
  }

  @Test void withholdsPositionWhenAmbiguousStaleOrUsingAnotherPublication() {
    assertNull(service.resolve(site, signals(-67, -67, now), now));
    assertNull(service.resolve(site, signals(-50, -81, now.minusSeconds(20)), now));
    assertNull(service.resolve(site, List.of(new Observation(beaconA, 1, 1, -51, now)), now));
    when(graphs.findCurrentBySite(site)).thenReturn(Optional.of(graph(UUID.randomUUID())));
    assertNull(service.resolve(site, signals(-50, -81, now), now));
    assertTrue(service.beacons(site).isEmpty());
  }

  private List<Observation> signals(double a, double b, Instant observedAt) {
    return List.of(new Observation(beaconA, 1, 1, a, observedAt),
        new Observation(beaconB, 1, 1, b, observedAt));
  }

  private PublishedSpatialGraph graph(UUID modelVersion) {
    return new PublishedSpatialGraph(site, UUID.randomUUID(), modelVersion,
        List.of(new Plan(floor, plan, 100, 100, .05)),
        List.of(new Node(first, floor, plan, 1, 2, NodeRole.REFERENCE),
            new Node(second, floor, plan, 50, 2, NodeRole.REFERENCE)), List.of());
  }
}
