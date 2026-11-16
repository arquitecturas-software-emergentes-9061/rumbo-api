package com.uitopic.rumbo.planning.domain.model.commands;

import java.util.Objects;
import java.util.UUID;

/** Requests a new draft copied from a site's current published map version. */
public record CreateDraftMapVersionCommand(UUID siteId, UUID mapId, UUID sourceVersionId) {
  public CreateDraftMapVersionCommand {
    Objects.requireNonNull(siteId, "siteId is required");
    Objects.requireNonNull(mapId, "mapId is required");
    Objects.requireNonNull(sourceVersionId, "sourceVersionId is required");
  }
}
