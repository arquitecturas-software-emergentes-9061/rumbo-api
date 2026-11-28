package com.uitopic.rumbo.analytics.domain.model.valueobjects;

/** Kind of problem a visitor reports on the way (US-26, TS-31). */
public enum IncidentCategory {
  /** A corridor, door or connection of the route could not be used. */
  BLOCKED_PATH,
  /** The guidance pointed in a wrong direction. */
  WRONG_DIRECTIONS,
  /** The map does not match the place, for example a missing room or a wrong label. */
  MAP_MISMATCH,
  /** The app lost its position, usually around a beacon. */
  SIGNAL_LOST,
  /** Stairs, steps or another barrier on a route that should avoid them. */
  ACCESSIBILITY_BARRIER,
  OTHER
}
