package com.uitopic.rumbo.iam.infrastructure.security;

import com.uitopic.rumbo.iam.application.ports.SubscriptionStatusReader.Standing;
import org.springframework.security.access.AccessDeniedException;

/**
 * Denies an administrative write because the clinic has no active plan (TS-28, MU-09d). It is an
 * {@link AccessDeniedException}, so the security chain answers it; {@link ProblemDetailSecurityHandler} gives it the
 * {@value #CODE} code and the {@code standing}, so clients can tell it apart from a missing permission.
 */
public class SubscriptionRequiredException extends AccessDeniedException {
  public static final String CODE = "SUBSCRIPTION_REQUIRED";
  private final Standing standing;

  public SubscriptionRequiredException(Standing standing) {
    super(standing == Standing.NONE
      ? "The clinic has no subscription; choose a plan to manage its sites"
      : "The clinic's subscription is not active; renew the plan to manage its sites");
    this.standing = standing;
  }

  public Standing standing() { return standing; }
}
