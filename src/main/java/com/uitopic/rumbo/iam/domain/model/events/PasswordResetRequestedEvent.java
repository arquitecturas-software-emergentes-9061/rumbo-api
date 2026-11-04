package com.uitopic.rumbo.iam.domain.model.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when the owner of an account asks to recover it (US-28). Communications sends the email with the link
 * that carries {@code resetToken}; IAM only keeps the digest of the token.
 *
 * @param userId account to recover
 * @param email address the link is sent to
 * @param resetToken single-use secret for the link; never logged
 * @param expiresAt instant after which the link no longer works
 * @param occurredAt instant of the request
 */
public record PasswordResetRequestedEvent(UUID userId, String email, String resetToken, Instant expiresAt,
                                          Instant occurredAt) {
  @Override
  public String toString() {
    return "PasswordResetRequestedEvent[userId=" + userId + ", expiresAt=" + expiresAt + "]";
  }
}
