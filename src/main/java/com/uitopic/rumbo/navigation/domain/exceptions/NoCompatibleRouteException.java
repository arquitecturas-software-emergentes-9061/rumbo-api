package com.uitopic.rumbo.navigation.domain.exceptions;

/** A route exists but does not satisfy the requested accessibility preference. */
public final class NoCompatibleRouteException extends RuntimeException {
  public NoCompatibleRouteException() {
    super("No route satisfies the requested accessibility preference");
  }
}
