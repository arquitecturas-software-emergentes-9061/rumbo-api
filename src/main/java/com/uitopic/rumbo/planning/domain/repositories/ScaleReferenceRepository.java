package com.uitopic.rumbo.planning.domain.repositories;

import com.uitopic.rumbo.planning.domain.model.valueobjects.ScaleReference;
import java.util.Optional;
import java.util.UUID;

/** Stores calibration provenance and updates the draft plan's authoritative scale together. */
public interface ScaleReferenceRepository {
  boolean existsOnDifferentFloor(UUID siteId, UUID planVersionId, UUID floorId);
  Optional<ScaleReference> upsert(ScaleReference reference, UUID modelVersionId);
  Optional<ScaleReference> upsertOriginal(ScaleReference reference);
  Optional<ScaleReference> find(UUID siteId, UUID planVersionId);
}
