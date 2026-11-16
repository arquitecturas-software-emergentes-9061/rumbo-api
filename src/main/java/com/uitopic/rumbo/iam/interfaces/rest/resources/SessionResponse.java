package com.uitopic.rumbo.iam.interfaces.rest.resources;

import java.time.Instant;
import java.util.UUID;

/** Active session of the caller; {@code current} marks the one the request was made with. */
public record SessionResponse(UUID id, Instant createdAt, Instant expiresAt, boolean current) {
}
