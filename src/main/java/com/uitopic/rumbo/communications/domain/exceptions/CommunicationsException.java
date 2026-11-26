package com.uitopic.rumbo.communications.domain.exceptions;

/** Base of communications business errors, identified by a stable code. */
public abstract class CommunicationsException extends RuntimeException {
  private final String code;

  protected CommunicationsException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() { return code; }
}
