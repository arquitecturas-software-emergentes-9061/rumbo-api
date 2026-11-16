package com.uitopic.rumbo.iam.domain.model.valueobjects;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Rule that locks an account after consecutive failed sign-in attempts.
 *
 * @param maxFailedAttempts consecutive failures that trigger the lock
 * @param lockDuration how long the account stays locked
 */
public record LockoutPolicy(int maxFailedAttempts, Duration lockDuration) {
  public static final LockoutPolicy DEFAULT = new LockoutPolicy(5, Duration.ofMinutes(15));

  public LockoutPolicy {
    Objects.requireNonNull(lockDuration, "lockDuration is required");
    if (maxFailedAttempts < 1) throw new IllegalArgumentException("maxFailedAttempts must be positive");
    if (lockDuration.isNegative() || lockDuration.isZero()) throw new IllegalArgumentException("lockDuration must be positive");
  }

  public boolean isReachedBy(int failedAttempts) { return failedAttempts >= maxFailedAttempts; }

  public Instant lockUntil(Instant now) { return now.plus(lockDuration); }
}
