package com.uitopic.rumbo.planning.domain.model.entities;

import com.uitopic.rumbo.planning.domain.model.valueobjects.FloorPlanFile;
import java.time.Instant;
import java.util.UUID;

/** An immutable architectural file revision for a floor, independent of indoor graph versions. */
public record FloorPlanVersion(UUID id, UUID siteId, UUID floorId, int number, FloorPlanFile file, Instant createdAt) {}
