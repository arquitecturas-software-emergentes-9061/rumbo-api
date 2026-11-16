package com.uitopic.rumbo.analytics.domain.model.valueobjects;

/** Facts of a navigation session that Analytics records. */
public enum NavigationEventType {
  NAVIGATION_STARTED,
  DEVIATION_DETECTED,
  ROUTE_RECALCULATED,
  NAVIGATION_FINISHED
}
