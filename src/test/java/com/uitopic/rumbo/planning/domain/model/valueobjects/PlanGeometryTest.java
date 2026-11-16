package com.uitopic.rumbo.planning.domain.model.valueobjects;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanGeometryTest {
  private final UUID floorId = UUID.randomUUID();
  private final UUID planVersionId = UUID.randomUUID();
  private final PlanCoordinateSpace space = new PlanCoordinateSpace(floorId, planVersionId, 100, 80);

  private SpatialCoordinate point(double x, double y) {
    return new SpatialCoordinate(floorId, planVersionId, x, y);
  }

  @Test
  void calibratesMetersPerPlanUnitFromReferencePoints() {
    MapScale scale = MapScale.calibrate(space, point(10, 20), point(40, 60), 10);
    assertEquals(floorId, scale.floorId());
    assertEquals(planVersionId, scale.planVersionId());
    assertEquals(0.2, scale.metersPerUnit(), 1e-12);
    assertEquals(15, scale.toMeters(75), 1e-12);
    assertEquals(0, scale.toMeters(0));
  }

  @Test
  void acceptsPlanCornersAndRejectsOutOfBoundsOrWrongVersion() {
    assertTrue(space.contains(point(0, 0)));
    assertTrue(space.contains(point(100, 80)));
    assertFalse(space.contains(point(-0.1, 2)));
    assertFalse(space.contains(point(100.1, 2)));
    assertFalse(space.contains(new SpatialCoordinate(floorId, UUID.randomUUID(), 1, 1)));
    assertFalse(space.contains(new SpatialCoordinate(UUID.randomUUID(), planVersionId, 1, 1)));
    assertThrows(IllegalArgumentException.class,
        () -> MapScale.calibrate(space, point(1, 1), point(101, 1), 3));
  }

  @Test
  void rejectsCoincidentPointsAndInvalidPhysicalDistance() {
    assertThrows(IllegalArgumentException.class,
        () -> MapScale.calibrate(space, point(5, 5), point(5, 5), 1));
    assertThrows(IllegalArgumentException.class,
        () -> MapScale.calibrate(space, point(5, 5), point(10, 5), 0));
    assertThrows(IllegalArgumentException.class,
        () -> MapScale.calibrate(space, point(5, 5), point(10, 5), -1));
    assertThrows(IllegalArgumentException.class,
        () -> MapScale.calibrate(space, point(5, 5), point(10, 5), Double.NaN));
  }

  @Test
  void rejectsNonFiniteGeometryAndScale() {
    assertThrows(IllegalArgumentException.class,
        () -> new PlanCoordinateSpace(floorId, planVersionId, Double.POSITIVE_INFINITY, 10));
    assertThrows(IllegalArgumentException.class,
        () -> new PlanCoordinateSpace(floorId, planVersionId, 10, 0));
    assertThrows(IllegalArgumentException.class,
        () -> new SpatialCoordinate(floorId, planVersionId, Double.NaN, 1));
    assertThrows(IllegalArgumentException.class, () -> new MapScale(floorId, planVersionId, Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> new MapScale(floorId, planVersionId, 0));
    assertThrows(IllegalArgumentException.class,
        () -> new MapScale(floorId, planVersionId, 1).toMeters(-1));
  }
}
