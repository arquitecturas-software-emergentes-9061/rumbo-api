package com.uitopic.rumbo.planning.application.internal.exceptions;

/** Raised when stored published data cannot form a valid successor draft. */
public final class InvalidPublishedMapVersionException extends RuntimeException {
  public InvalidPublishedMapVersionException(Throwable cause) {
    super("Published map version cannot be copied", cause);
  }
}
