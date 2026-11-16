package com.uitopic.rumbo.iam.domain.model.valueobjects;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * How long a recovery link stays valid (US-28). Short enough that a leaked email is useless soon after, long enough
 * for the user to open it.
 *
 * @param tokenLifetime validity of a recovery link
 */
public record PasswordRecoveryPolicy(Duration tokenLifetime) {
  public static final PasswordRecoveryPolicy DEFAULT = new PasswordRecoveryPolicy(Duration.ofMinutes(30));

  public PasswordRecoveryPolicy {
    Objects.requireNonNull(tokenLifetime, "tokenLifetime is required");
    if (tokenLifetime.isNegative() || tokenLifetime.isZero()) {
      throw new IllegalArgumentException("tokenLifetime must be positive");
    }
  }

  public Instant expiresAt(Instant now) { return now.plus(tokenLifetime); }
}
