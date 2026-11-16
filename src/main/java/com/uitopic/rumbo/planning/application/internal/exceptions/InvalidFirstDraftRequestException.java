package com.uitopic.rumbo.planning.application.internal.exceptions;

/** Raised when the plans selected for a first draft cannot form one coherent map. */
public final class InvalidFirstDraftRequestException extends RuntimeException {
  public InvalidFirstDraftRequestException(String message) {
    super(message);
  }
}
