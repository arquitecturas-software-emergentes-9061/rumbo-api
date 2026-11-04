package com.uitopic.rumbo.planning.application.internal.exceptions;

/** Raised when a map already has a draft being edited. */
public final class DraftMapVersionAlreadyExistsException extends RuntimeException {
  public DraftMapVersionAlreadyExistsException() {
    super("An indoor map draft already exists");
  }
}
