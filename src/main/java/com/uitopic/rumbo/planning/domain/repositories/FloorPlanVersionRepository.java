package com.uitopic.rumbo.planning.domain.repositories;

import com.uitopic.rumbo.planning.domain.model.entities.FloorPlanVersion;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface FloorPlanVersionRepository {
  record Original(FloorPlanVersion version, byte[] bytes) {}

  /** Lock the existing ownership row so competing appends for the same floor are serialized. */
  boolean lockFloor(UUID siteId, UUID floorId);
  int nextVersion(UUID floorId);
  void insert(FloorPlanVersion version, byte[] bytes);
  /** Empty optional means the floor does not belong to the requested site. */
  Optional<List<FloorPlanVersion>> findVersions(UUID siteId, UUID floorId);
  Optional<Original> findOriginal(UUID siteId, UUID floorId, UUID versionId);
}
