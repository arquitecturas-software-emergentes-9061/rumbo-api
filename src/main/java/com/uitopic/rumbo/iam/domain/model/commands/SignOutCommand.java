package com.uitopic.rumbo.iam.domain.model.commands;

import java.util.Objects;
import java.util.UUID;

/**
 * Request to end one session of an account: the current one (sign-out) or another device's session.
 *
 * @param userId account that asks; only its own sessions can be ended
 * @param sessionId session to end
 */
public record SignOutCommand(UUID userId, UUID sessionId) {
  public SignOutCommand {
    Objects.requireNonNull(userId, "userId is required");
    Objects.requireNonNull(sessionId, "sessionId is required");
  }
}
