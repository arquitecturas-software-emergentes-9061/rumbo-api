package com.uitopic.rumbo.navigation.interfaces.rest.resources;

import com.uitopic.rumbo.navigation.domain.model.valueobjects.DirectoryEntry;
import java.util.UUID;

/** Published directory result with its version and destination node. */
public record DirectoryEntryResponse(UUID id, UUID siteId, UUID modelVersionId,
                                     UUID floorId, UUID nodeId, DirectoryEntry.EntryType type,
                                     String name, String specialty) {
  public static DirectoryEntryResponse from(DirectoryEntry entry) {
    return new DirectoryEntryResponse(entry.id(), entry.siteId(), entry.modelVersionId(),
        entry.floorId(), entry.nodeId(), entry.type(), entry.name(), entry.specialty());
  }
}
