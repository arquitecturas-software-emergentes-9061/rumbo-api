package com.uitopic.rumbo.planning.domain.model.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A version of the indoor map of a site was published and is now the one navigation uses. Published after the
 * publication commits; other contexts react to it without reading Planning's model.
 *
 * @param modelVersionId the published version, unique per publication
 */
public record MapVersionPublishedEvent(UUID siteId, UUID mapId, UUID modelVersionId, Instant publishedAt) {
  public MapVersionPublishedEvent {
    Objects.requireNonNull(siteId, "siteId is required");
    Objects.requireNonNull(mapId, "mapId is required");
    Objects.requireNonNull(modelVersionId, "modelVersionId is required");
    Objects.requireNonNull(publishedAt, "publishedAt is required");
  }
}
