package com.uitopic.rumbo.planning.domain.model.valueobjects;

import java.util.UUID;

/** Bounds of one locked draft floor plan, measured in plan coordinate units. */
public record DraftPlanBounds(UUID modelVersionId, double widthUnits, double heightUnits) {
  public DraftPlanBounds {
    if (modelVersionId == null || !Double.isFinite(widthUnits) || widthUnits <= 0
        || !Double.isFinite(heightUnits) || heightUnits <= 0)
      throw new IllegalArgumentException("Invalid draft plan bounds");
  }

  /** Whether both reference points lie inside this plan's inclusive bounds. */
  public boolean contains(ScaleReference reference) {
    return reference.startX() <= widthUnits && reference.endX() <= widthUnits
        && reference.startY() <= heightUnits && reference.endY() <= heightUnits;
  }
}
