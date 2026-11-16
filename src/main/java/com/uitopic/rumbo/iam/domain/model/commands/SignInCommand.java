package com.uitopic.rumbo.iam.domain.model.commands;

/**
 * Request to verify credentials and open a session.
 *
 * @param email email of the account
 * @param password plain password to verify
 */
public record SignInCommand(String email, String password) {
  @Override
  public String toString() { return "SignInCommand[email=" + email + "]"; }
}
