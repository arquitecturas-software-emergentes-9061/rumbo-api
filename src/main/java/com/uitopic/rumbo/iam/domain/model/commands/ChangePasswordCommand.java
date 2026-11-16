package com.uitopic.rumbo.iam.domain.model.commands;

import java.util.Objects;
import java.util.UUID;

/**
 * Changes the password of a signed-in account, confirming the current one first.
 *
 * @param userId account that asks
 * @param sessionId session the request comes from; it stays open, every other session is ended
 * @param currentPassword password the user has today
 * @param newPassword password chosen by the user
 */
public record ChangePasswordCommand(UUID userId, UUID sessionId, String currentPassword, String newPassword) {
  public ChangePasswordCommand {
    Objects.requireNonNull(userId, "userId is required");
    Objects.requireNonNull(sessionId, "sessionId is required");
  }

  @Override
  public String toString() {
    return "ChangePasswordCommand[userId=" + userId + "]";
  }
}
