package com.uitopic.rumbo.iam.domain.model.commands;

/**
 * Sets a new password with the secret of a recovery link (US-28).
 *
 * @param resetToken secret received by email
 * @param newPassword password chosen by the user
 */
public record ResetPasswordCommand(String resetToken, String newPassword) {
  @Override
  public String toString() {
    return "ResetPasswordCommand[***]";
  }
}
