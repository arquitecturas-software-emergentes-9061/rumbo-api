package com.uitopic.rumbo.navigation.domain;
import java.util.UUID;
/** Read-only destination from a published model, NOT an editable floorplan. */
public record Destination(UUID id, UUID siteId, UUID floorId, UUID nodeId, String name,
                          String specialty, String category, boolean accessible) {
  public Destination {
    if (id == null || siteId == null || floorId == null || nodeId == null)
      throw new IllegalArgumentException("Destination identifiers required");
    if (name == null || name.isBlank()) throw new IllegalArgumentException("Destination name required");
  }
}
