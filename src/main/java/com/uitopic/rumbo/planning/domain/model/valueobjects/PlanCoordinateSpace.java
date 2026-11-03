package com.uitopic.rumbo.planning.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/**
 * Rectangular visual bounds belonging to one version of a floor plan.
 * Coordinates range from (0, 0) to (widthUnits, heightUnits), inclusive.
 * The origin is at the top left; dimensions are in plan units, not meters.
 *
 * @param floorId floor shown by the plan
 * @param planVersionId plan version defining the coordinate system
 * @param widthUnits width in unscaled plan units
 * @param heightUnits height in unscaled plan units
 */
public record PlanCoordinateSpace(UUID floorId, UUID planVersionId,
                                  double widthUnits, double heightUnits) {
  public PlanCoordinateSpace {
    Objects.requireNonNull(floorId, "floorId is required");
    Objects.requireNonNull(planVersionId, "planVersionId is required");
    if (!Double.isFinite(widthUnits) || widthUnits <= 0
        || !Double.isFinite(heightUnits) || heightUnits <= 0) {
      throw new IllegalArgumentException("Plan dimensions must be finite and positive");
    }
  }

  /**
   * Checks whether a coordinate belongs to this version and lies within its visual bounds.
   *
   * @param coordinate position to inspect
   * @return true when floor, version and bounds all match
   */
  public boolean contains(SpatialCoordinate coordinate) {
    return coordinate != null
        && floorId.equals(coordinate.floorId())
        && planVersionId.equals(coordinate.planVersionId())
        && coordinate.x() >= 0 && coordinate.x() <= widthUnits
        && coordinate.y() >= 0 && coordinate.y() <= heightUnits;
  }

  /**
   * Measures straight-line distance between two positions in this plan's visual units.
   *
   * @throws IllegalArgumentException if either position is outside this plan version
   */
  public double distanceUnits(SpatialCoordinate first, SpatialCoordinate second) {
    if (!contains(first) || !contains(second)) {
      throw new IllegalArgumentException("Both coordinates must belong to this plan version and its bounds");
    }
    double distance = Math.hypot(first.x() - second.x(), first.y() - second.y());
    if (!Double.isFinite(distance)) {
      throw new IllegalArgumentException("Coordinate distance exceeds the supported range");
    }
    return distance;
  }
}
