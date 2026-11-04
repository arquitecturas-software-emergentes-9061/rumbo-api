package com.uitopic.rumbo.iam.domain.model.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when the password of an account changes, by recovery or from the profile. Communications warns the owner
 * so an unexpected change can be reported.
 *
 * @param userId account whose password changed
 * @param occurredAt instant of the change
 */
public record UserPasswordChangedEvent(UUID userId, Instant occurredAt) {
}
