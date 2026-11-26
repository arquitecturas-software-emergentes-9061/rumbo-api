package com.uitopic.rumbo.navigation.domain.model.valueobjects;

/**
 * Mobility restrictions applied when a route is calculated (TS-29, US-24). They come from the request or from the
 * visitor's saved accessibility preferences in Profiles and Preferences.
 * <ul>
 *   <li>{@code avoidStairs}: stairs are never part of the route.</li>
 *   <li>{@code requiresAccessibleRoute}: only connections with confirmed accessibility are used, and stairs are
 *   excluded even if they were classified as accessible, because a step-free route is what the visitor asked for.</li>
 *   <li>{@code prioritizeElevators}: stairs and ramps stay allowed but cost {@link #NON_ELEVATOR_VERTICAL_FACTOR}
 *   times more, so an elevator wins whenever one reaches the destination at a comparable cost.</li>
 * </ul>
 * A restriction never adds connections: if no route satisfies all of them, there is no route.
 */
public record RouteConstraints(boolean avoidStairs, boolean requiresAccessibleRoute, boolean prioritizeElevators) {
  /** Cost multiplier for stairs and ramps when elevators are prioritized; at least 1, so A* stays admissible. */
  public static final double NON_ELEVATOR_VERTICAL_FACTOR = 3.0;

  public static final RouteConstraints NONE = new RouteConstraints(false, false, false);

  /** Keeps the meaning of the former {@code accessibleOnly} flag. */
  public static RouteConstraints accessibleOnly(boolean accessibleOnly) {
    return accessibleOnly ? new RouteConstraints(false, true, false) : NONE;
  }

  /** True when the connection may be traversed under these restrictions. */
  public boolean allows(PublishedSpatialGraph.Link link) {
    boolean stairs = link.kind() == PublishedSpatialGraph.LinkKind.STAIRS;
    if ((avoidStairs || requiresAccessibleRoute) && stairs) return false;
    return !requiresAccessibleRoute || link.accessibility() == PublishedSpatialGraph.Accessibility.ACCESSIBLE;
  }

  /** Search cost of an allowed connection; the reported route cost stays the published one. */
  public double searchCost(PublishedSpatialGraph.Link link) {
    double published = link.weight() + link.verticalPenalty();
    boolean stairsOrRamp = link.kind() == PublishedSpatialGraph.LinkKind.STAIRS
        || link.kind() == PublishedSpatialGraph.LinkKind.RAMP;
    return prioritizeElevators && stairsOrRamp ? published * NON_ELEVATOR_VERTICAL_FACTOR : published;
  }

  /** Combines two sets of restrictions; the result is at least as strict as both. */
  public RouteConstraints and(RouteConstraints other) {
    return new RouteConstraints(avoidStairs || other.avoidStairs,
        requiresAccessibleRoute || other.requiresAccessibleRoute, prioritizeElevators || other.prioritizeElevators);
  }
}
