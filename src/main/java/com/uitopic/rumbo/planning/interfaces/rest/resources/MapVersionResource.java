package com.uitopic.rumbo.planning.interfaces.rest.resources;

import com.uitopic.rumbo.planning.domain.model.valueobjects.MapVersionStatus;
import java.util.UUID;

/** Published map-version identity and resulting lifecycle state. */
public record MapVersionResource(UUID siteId, UUID mapId, UUID modelVersionId,
                                 MapVersionStatus status) {}
