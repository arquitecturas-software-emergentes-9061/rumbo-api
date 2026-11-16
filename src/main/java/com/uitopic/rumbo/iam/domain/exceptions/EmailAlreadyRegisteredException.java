package com.uitopic.rumbo.iam.domain.exceptions;

/** The email already belongs to an account (TS-26, scenario 2). */
public class EmailAlreadyRegisteredException extends IamException {
  public EmailAlreadyRegisteredException() {
    super("EMAIL_ALREADY_REGISTERED", "An account with this email already exists");
  }
}
