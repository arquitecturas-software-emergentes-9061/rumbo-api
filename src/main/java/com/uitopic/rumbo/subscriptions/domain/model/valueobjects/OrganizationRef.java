package com.uitopic.rumbo.subscriptions.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

public record OrganizationRef(UUID id, String name) {
  public OrganizationRef {
    Objects.requireNonNull(id, "Organization ID is required");
    if (name == null || name.isBlank() || name.strip().length() > 160)
      throw new IllegalArgumentException("Organization name must contain 1 to 160 characters");
    name = name.strip();
  }
}
