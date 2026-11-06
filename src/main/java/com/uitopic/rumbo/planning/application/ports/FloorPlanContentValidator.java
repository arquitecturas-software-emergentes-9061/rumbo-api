package com.uitopic.rumbo.planning.application.ports;

import com.uitopic.rumbo.planning.domain.model.valueobjects.FloorPlanFile.Format;

/** Checks that the uploaded bytes form a supported file before any version is written. */
public interface FloorPlanContentValidator {
  void validate(Format format, byte[] bytes);
}
