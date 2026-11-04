package com.uitopic.rumbo.iam.domain.exceptions;

/**
 * The recovery link is unknown, expired or was already used (US-28). One message covers every case; the user asks
 * for a new link.
 */
public class InvalidPasswordResetTokenException extends IamException {
  public InvalidPasswordResetTokenException() {
    super("INVALID_PASSWORD_RESET_TOKEN", "The recovery link is no longer valid, request a new one");
  }
}
