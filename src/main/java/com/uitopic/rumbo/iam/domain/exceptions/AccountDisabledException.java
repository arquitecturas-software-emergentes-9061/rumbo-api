package com.uitopic.rumbo.iam.domain.exceptions;

/** The account was disabled by a clinic administrator. It is only reported after a correct password. */
public class AccountDisabledException extends IamException {
  public AccountDisabledException() {
    super("ACCOUNT_DISABLED", "The account is disabled");
  }
}
