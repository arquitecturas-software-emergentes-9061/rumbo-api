package com.uitopic.rumbo.navigation.domain.exceptions;

/** A requested node belongs to another site's current published model. */
public final class RouteNodeOutsideSiteException extends RuntimeException {
  public RouteNodeOutsideSiteException() {
    super("A route node belongs to another site");
  }
}
