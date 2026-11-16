package com.uitopic.rumbo.navigation.application.internal.exceptions;

/** No current published route exists for the requested nodes and preference. */
public final class RouteNotFoundException extends RuntimeException {
  public RouteNotFoundException() {
    super("No route is available for the requested nodes");
  }
}
