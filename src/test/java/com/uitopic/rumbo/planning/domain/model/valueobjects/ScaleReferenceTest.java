package com.uitopic.rumbo.planning.domain.model.valueobjects;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ScaleReferenceTest {
  private final UUID site = UUID.randomUUID(), floor = UUID.randomUUID(), version = UUID.randomUUID();

  @Test void convertsCentimetersIntoMetersPerPlanUnit() {
    var scale = ScaleReference.calibrate(site, floor, version,
      ScaleReference.Unit.CENTIMETERS, 300, 0, 0, 30, 0);
    assertEquals(0.1, scale.metersPerUnit(), 1e-10);
    assertEquals(300, scale.measuredDistance());
    assertEquals(ScaleReference.Unit.CENTIMETERS, scale.unit());
  }

  @Test void rejectsEqualPointsNegativeDistanceAndNonFiniteValues() {
    assertThrows(IllegalArgumentException.class, () ->
      ScaleReference.calibrate(site, floor, version, ScaleReference.Unit.METERS, 3, 1, 1, 1, 1));
    assertThrows(IllegalArgumentException.class, () ->
      ScaleReference.calibrate(site, floor, version, ScaleReference.Unit.METERS, -1, 0, 0, 10, 0));
    assertThrows(IllegalArgumentException.class, () ->
      ScaleReference.calibrate(site, floor, version, ScaleReference.Unit.METERS, 3, Double.NaN, 0, 10, 0));
  }
}
