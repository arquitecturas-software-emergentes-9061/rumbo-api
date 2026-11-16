package com.uitopic.rumbo.planning.domain.model.valueobjects;

import java.util.UUID;

/** Editable wall segment in one floor-plan coordinate space. */
public record MapWall(UUID id, UUID floorId, UUID planVersionId,
                      double x1, double y1, double x2, double y2) {
  public MapWall {
    if (id == null || floorId == null || planVersionId == null
        || !Double.isFinite(x1) || !Double.isFinite(y1)
        || !Double.isFinite(x2) || !Double.isFinite(y2)
        || Math.hypot(x2 - x1, y2 - y1) <= 1e-6) {
      throw new IllegalArgumentException("Invalid wall segment");
    }
  }
}
