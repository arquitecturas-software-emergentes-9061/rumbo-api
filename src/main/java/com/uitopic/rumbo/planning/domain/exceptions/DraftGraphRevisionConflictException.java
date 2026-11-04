package com.uitopic.rumbo.planning.domain.exceptions;

/** A draft edit was based on an older graph revision. */
public final class DraftGraphRevisionConflictException extends RuntimeException {
  public DraftGraphRevisionConflictException() {
    super("Draft graph changed since it was read");
  }
}
