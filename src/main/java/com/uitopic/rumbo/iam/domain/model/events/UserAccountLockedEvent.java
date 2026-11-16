package com.uitopic.rumbo.iam.domain.model.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an account reaches the failed sign-in threshold. Communications warns the owner.
 *
 * @param userId locked account
 * @param lockedUntil instant when the account can sign in again
 * @param occurredAt instant of the lock
 */
public record UserAccountLockedEvent(UUID userId, Instant lockedUntil, Instant occurredAt) {
}
