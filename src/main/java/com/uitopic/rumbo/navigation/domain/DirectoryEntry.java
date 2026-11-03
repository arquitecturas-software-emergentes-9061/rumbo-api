package com.uitopic.rumbo.navigation.domain;

import java.util.Objects;
import java.util.UUID;

/** Search result tied to a destination node in one published map version. */
public record DirectoryEntry(UUID id, UUID siteId, UUID modelVersionId, UUID floorId,
                             UUID nodeId, EntryType type, String name, String specialty) {
  public DirectoryEntry {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(siteId, "siteId is required");
    Objects.requireNonNull(modelVersionId, "modelVersionId is required");
    Objects.requireNonNull(floorId, "floorId is required");
    Objects.requireNonNull(nodeId, "nodeId is required");
    Objects.requireNonNull(type, "type is required");
    if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
  }

  /** Directory categories understood by Navigation without importing Planning types. */
  public enum EntryType { DOCTOR, SPECIALTY, SERVICE }
}
