package com.uitopic.rumbo.planning.domain.repositories;

import com.uitopic.rumbo.planning.domain.model.valueobjects.DraftPlanBounds;
import java.util.Optional;
import java.util.UUID;

/** Reads plan ownership and geometry needed by calibration and beacon placement. */
public interface PlanSpatialReferenceRepository {
  Optional<DraftPlanBounds> lockDraftPlan(UUID siteId, UUID floorId, UUID planVersionId);
  boolean containsPoint(UUID siteId, UUID floorId, double xUnits, double yUnits);
}
