package com.uitopic.rumbo.iam.interfaces.rest.resources;

import java.time.Instant;
import java.util.UUID;

public record UserAccountResponse(UUID id, String email, String role, String status, UUID clinicId,
                                  Instant createdAt) {
}
