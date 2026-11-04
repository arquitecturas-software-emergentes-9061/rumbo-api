package com.uitopic.rumbo.iam.domain.exceptions;

/** The current password given to change it does not match the account. */
public class IncorrectCurrentPasswordException extends IamException {
  public IncorrectCurrentPasswordException() {
    super("INCORRECT_CURRENT_PASSWORD", "The current password is not correct");
  }
}
