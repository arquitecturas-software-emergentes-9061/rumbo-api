package com.uitopic.rumbo.planning.domain.model.valueobjects;

/** Top-left coordinate space of an uploaded original, independent of the map graph. */
public record OriginalPlanBounds(double width, double height) {
  public OriginalPlanBounds {
    if (!(width > 0 && height > 0 && width <= 10_000_000 && height <= 10_000_000)) {
      throw new IllegalArgumentException("Invalid original plan dimensions");
    }
  }

  public boolean contains(ScaleReference reference) {
    return reference.startX() <= width && reference.endX() <= width
      && reference.startY() <= height && reference.endY() <= height;
  }
}
