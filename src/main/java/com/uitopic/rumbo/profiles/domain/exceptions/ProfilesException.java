package com.uitopic.rumbo.profiles.domain.exceptions;

/** Base of profile business errors, identified by a stable code. */
public abstract class ProfilesException extends RuntimeException {
  private final String code;

  protected ProfilesException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() { return code; }
}
