package com.uitopic.rumbo.planning.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/**
 * Confirmed conversion between one floor-plan version's visual units and physical meters.
 * A plan without a confirmed reference has no MapScale yet.
 *
 * @param floorId floor shown by the calibrated plan
 * @param planVersionId version for which the calibration is valid
 * @param metersPerUnit physical meters represented by one visual plan unit
 */
public record MapScale(UUID floorId, UUID planVersionId, double metersPerUnit) {
  public MapScale {
    Objects.requireNonNull(floorId, "floorId is required");
    Objects.requireNonNull(planVersionId, "planVersionId is required");
    if (!Double.isFinite(metersPerUnit) || metersPerUnit <= 0) {
      throw new IllegalArgumentException("Scale must be finite and positive");
    }
  }

  /**
   * Calibrates a plan from two distinct points and their measured physical separation.
   * Formula: metersPerUnit = realDistanceMeters / visualDistanceUnits.
   *
   * @param space floor-plan version containing both reference points
   * @param first first reference point in plan units
   * @param second second reference point in plan units
   * @param realDistanceMeters measured physical distance between the points
   * @return confirmed scale for the referenced plan version
   * @throws IllegalArgumentException if reference data cannot define a positive finite scale
   */
  public static MapScale calibrate(PlanCoordinateSpace space, SpatialCoordinate first,
                                   SpatialCoordinate second, double realDistanceMeters) {
    if (space == null || !Double.isFinite(realDistanceMeters) || realDistanceMeters <= 0) {
      throw new IllegalArgumentException("A plan and positive finite physical distance are required");
    }
    double visualDistance = space.distanceUnits(first, second);
    if (visualDistance == 0) {
      throw new IllegalArgumentException("Calibration points must be distinct");
    }
    return new MapScale(space.floorId(), space.planVersionId(), realDistanceMeters / visualDistance);
  }

  /**
   * Converts a nonnegative distance in plan units to physical meters.
   *
   * @throws IllegalArgumentException if the distance or result is not finite and nonnegative
   */
  public double toMeters(double distanceUnits) {
    if (!Double.isFinite(distanceUnits) || distanceUnits < 0) {
      throw new IllegalArgumentException("Plan distance must be finite and nonnegative");
    }
    double meters = distanceUnits * metersPerUnit;
    if (!Double.isFinite(meters)) {
      throw new IllegalArgumentException("Physical distance exceeds the supported range");
    }
    return meters;
  }
}
