package com.uitopic.rumbo.planning.domain.exceptions;

/** A rejected original file. These reasons are also used by the HTTP adapter. */
public final class InvalidFloorPlanFile extends RuntimeException {
  public enum Reason { INVALID_NAME, EMPTY_FILE, FILE_TOO_LARGE, UNSUPPORTED_TYPE, INVALID_CONTENT }

  private final Reason reason;

  public InvalidFloorPlanFile(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
