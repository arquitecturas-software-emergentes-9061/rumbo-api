package com.uitopic.rumbo.planning.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/**
 * A position in the unscaled visual units of one floor-plan version.
 * The origin is at the top left; x increases rightward and y downward.
 * Bounds depend on the referenced {@link PlanCoordinateSpace}.
 *
 * @param floorId floor containing the position
 * @param planVersionId version whose visual units define the position
 * @param x horizontal coordinate in plan units
 * @param y vertical coordinate in plan units
 */
public record SpatialCoordinate(UUID floorId, UUID planVersionId, double x, double y) {
  public SpatialCoordinate {
    Objects.requireNonNull(floorId, "floorId is required");
    Objects.requireNonNull(planVersionId, "planVersionId is required");
    if (!Double.isFinite(x) || !Double.isFinite(y)) {
      throw new IllegalArgumentException("Coordinates must be finite");
    }
  }
}
