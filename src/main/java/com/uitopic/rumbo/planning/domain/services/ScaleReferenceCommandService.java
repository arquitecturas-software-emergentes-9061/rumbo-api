package com.uitopic.rumbo.planning.domain.services;

import com.uitopic.rumbo.planning.domain.model.valueobjects.ScaleReference;
import java.util.UUID;

/** Calibrates one draft floor plan from a measured physical reference. */
public interface ScaleReferenceCommandService {
  ScaleReference upsert(UUID ownerUserId, ScaleReference reference);
}
