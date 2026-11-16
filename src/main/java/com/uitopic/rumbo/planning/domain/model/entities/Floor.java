package com.uitopic.rumbo.planning.domain.model.entities;

import java.util.Objects;
import java.util.UUID;

/** A registered floor; negative levels are valid for basements. */
public record Floor(UUID id, UUID siteId, int number, String name, String description) {
  public Floor {
    Objects.requireNonNull(id, "Floor ID is required");
    Objects.requireNonNull(siteId, "Site ID is required");
    if (name == null || name.strip().length() < 2 || name.strip().length() > 100)
      throw new IllegalArgumentException("Floor name must contain 2-100 characters");
    name = name.strip();
    description = description == null || description.isBlank() ? null : description.strip();
    if (description != null && description.length() > 250) throw new IllegalArgumentException("Description is too long");
  }
}
