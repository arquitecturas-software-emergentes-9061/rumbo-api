package com.uitopic.rumbo.navigation.domain.exceptions;

/** Published spatial data violates the invariants required by route calculation. */
public final class InvalidPublishedGraphException extends RuntimeException {
  public InvalidPublishedGraphException(Throwable cause) {
    super("The published model cannot be used for route calculation", cause);
  }
}
