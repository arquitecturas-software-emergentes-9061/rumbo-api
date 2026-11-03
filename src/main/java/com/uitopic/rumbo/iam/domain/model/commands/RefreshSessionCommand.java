package com.uitopic.rumbo.iam.domain.model.commands;

/**
 * Request to rotate a session and obtain new credentials.
 *
 * @param refreshToken raw refresh token received at sign-in or at the previous refresh
 */
public record RefreshSessionCommand(String refreshToken) {
  @Override
  public String toString() { return "RefreshSessionCommand[refreshToken=***]"; }
}
