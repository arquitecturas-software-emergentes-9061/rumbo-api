package com.uitopic.rumbo.iam.domain.exceptions;

/**
 * Base of the business errors of Identity and Access Control. {@link #code()} is the stable identifier that the
 * API returns, so clients can react without parsing messages.
 */
public abstract class IamException extends RuntimeException {
  private final String code;

  protected IamException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() { return code; }
}
