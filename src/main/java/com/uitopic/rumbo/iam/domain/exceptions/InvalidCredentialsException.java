package com.uitopic.rumbo.iam.domain.exceptions;

/**
 * Email or password are wrong. The same message is used for an unknown email and a wrong password,
 * so the response does not reveal which accounts exist (TS-01, scenario 2).
 */
public class InvalidCredentialsException extends IamException {
  public InvalidCredentialsException() {
    super("INVALID_CREDENTIALS", "Email or password is incorrect");
  }
}
