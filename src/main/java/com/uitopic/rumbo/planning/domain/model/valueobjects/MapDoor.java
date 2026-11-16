package com.uitopic.rumbo.planning.domain.model.valueobjects;

import java.util.UUID;

/** Opening measured along its wall, with fractions from the first endpoint. */
public record MapDoor(UUID id, UUID wallId, double from, double to) {
  public MapDoor {
    if (id == null || wallId == null || !Double.isFinite(from) || !Double.isFinite(to)
        || from < 0 || to > 1 || to - from <= 1e-6) {
      throw new IllegalArgumentException("Invalid door opening");
    }
  }
}
