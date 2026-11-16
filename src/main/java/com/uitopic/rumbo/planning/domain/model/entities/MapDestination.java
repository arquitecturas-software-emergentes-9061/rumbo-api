package com.uitopic.rumbo.planning.domain.model.entities;

import com.uitopic.rumbo.planning.domain.model.valueobjects.DirectoryEntryType;
import java.util.Objects;
import java.util.UUID;

/** Medical directory entry attached to a destination node in one map version. */
public record MapDestination(UUID id, UUID modelVersionId, UUID nodeId,
                             DirectoryEntryType type, String name, String specialty) {
  public MapDestination {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(modelVersionId, "modelVersionId is required");
    Objects.requireNonNull(nodeId, "nodeId is required");
    Objects.requireNonNull(type, "type is required");
    if (name == null || name.isBlank() || name.length() > 150) {
      throw new IllegalArgumentException("Destination name must contain 1 to 150 characters");
    }
    if (specialty != null && (specialty.isBlank() || specialty.length() > 150)) {
      throw new IllegalArgumentException("Specialty must contain 1 to 150 characters when supplied");
    }
    name = name.strip();
    if (specialty != null) specialty = specialty.strip();
  }
}
