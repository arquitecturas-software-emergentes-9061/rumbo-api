package com.uitopic.rumbo.iam.interfaces.security;

import com.uitopic.rumbo.iam.domain.model.valueobjects.UserRole;

import java.util.Optional;
import java.util.UUID;

/**
 * Caller of the current request, taken from a verified access token. Controllers of any bounded context receive it
 * with {@code @AuthenticationPrincipal AuthenticatedPrincipal principal}.
 */
public record AuthenticatedPrincipal(UUID userId, UserRole role, UUID sessionId, UUID clinicId) {
  public Optional<UUID> clinic() { return Optional.ofNullable(clinicId); }

  public boolean isClinicAdmin() { return role == UserRole.CLINIC_ADMIN; }
}
