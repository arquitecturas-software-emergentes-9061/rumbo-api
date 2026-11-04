package com.uitopic.rumbo.planning.domain.exceptions;

/** A stored published graph cannot be interpreted as a valid Planning model. */
public final class InvalidPublishedMapDataException extends RuntimeException {
  public InvalidPublishedMapDataException(Throwable cause) {
    super("Stored published map data is invalid", cause);
  }
}
