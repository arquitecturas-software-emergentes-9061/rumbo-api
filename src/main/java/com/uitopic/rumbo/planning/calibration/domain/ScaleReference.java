package com.uitopic.rumbo.planning.calibration.domain;

import java.util.Objects;
import java.util.UUID;

/** Physical scale reference attached to exactly one floor and one floor-plan version. */
public record ScaleReference(UUID siteId, UUID floorId, UUID planVersionId, Unit unit,
                             double measuredDistance, double startX, double startY,
                             double endX, double endY, double metersPerUnit) {
  public enum Unit { METERS, CENTIMETERS }

  public ScaleReference {
    Objects.requireNonNull(siteId, "siteId required");
    Objects.requireNonNull(floorId, "floorId required");
    Objects.requireNonNull(planVersionId, "planVersionId required");
    Objects.requireNonNull(unit, "unit required");
    if (!Double.isFinite(measuredDistance) || measuredDistance <= 0
      || !valid(startX) || !valid(startY) || !valid(endX) || !valid(endY)
      || !Double.isFinite(metersPerUnit) || metersPerUnit <= 0)
      throw new IllegalArgumentException("Invalid measured distance, coordinates, or scale");
    if (Math.hypot(startX - endX, startY - endY) == 0)
      throw new IllegalArgumentException("Reference points must be distinct");
  }

  public static ScaleReference calibrate(UUID siteId, UUID floorId, UUID planVersionId,
      Unit unit, double measuredDistance, double startX, double startY, double endX, double endY) {
    if (unit == null) throw new IllegalArgumentException("Measurement unit required");
    if (!Double.isFinite(measuredDistance) || measuredDistance <= 0)
      throw new IllegalArgumentException("Physical distance must be positive and finite");
    if (!valid(startX) || !valid(startY) || !valid(endX) || !valid(endY))
      throw new IllegalArgumentException("Plan coordinates must be finite and non-negative");
    double visualDistance = Math.hypot(startX - endX, startY - endY);
    if (!(visualDistance > 0) || !Double.isFinite(visualDistance))
      throw new IllegalArgumentException("Reference points must be distinct");
    double factor = (unit == Unit.CENTIMETERS ? measuredDistance / 100.0 : measuredDistance)
      / visualDistance;
    return new ScaleReference(siteId, floorId, planVersionId, unit, measuredDistance,
      startX, startY, endX, endY, factor);
  }

  private static boolean valid(double value) {
    return Double.isFinite(value) && value >= 0 && value <= 10_000_000;
  }
}
