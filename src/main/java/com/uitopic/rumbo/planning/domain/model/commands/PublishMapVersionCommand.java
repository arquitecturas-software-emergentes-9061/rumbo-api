package com.uitopic.rumbo.planning.domain.model.commands;

import java.util.Objects;
import java.util.UUID;

/** Request to publish one draft version belonging to a site and indoor map. */
public record PublishMapVersionCommand(UUID siteId, UUID mapId, UUID modelVersionId) {
  public PublishMapVersionCommand {
    Objects.requireNonNull(siteId, "siteId is required");
    Objects.requireNonNull(mapId, "mapId is required");
    Objects.requireNonNull(modelVersionId, "modelVersionId is required");
  }
}
