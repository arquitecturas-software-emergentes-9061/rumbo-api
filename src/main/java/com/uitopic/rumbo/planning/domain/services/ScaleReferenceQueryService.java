package com.uitopic.rumbo.planning.domain.services;

import com.uitopic.rumbo.planning.domain.model.valueobjects.ScaleReference;
import java.util.UUID;

/** Reads the persisted provenance of a plan's physical scale. */
public interface ScaleReferenceQueryService {
  ScaleReference find(UUID ownerUserId, UUID siteId, UUID planVersionId);
}
