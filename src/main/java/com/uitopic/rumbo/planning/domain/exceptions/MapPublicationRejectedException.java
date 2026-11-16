package com.uitopic.rumbo.planning.domain.exceptions;

import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphValidationResult;
import java.util.Objects;

/** A draft cannot be published because its graph fails domain validation. */
public final class MapPublicationRejectedException extends RuntimeException {
  private final GraphValidationResult validation;

  public MapPublicationRejectedException(GraphValidationResult validation) {
    super("Indoor map version cannot be published");
    this.validation = Objects.requireNonNull(validation, "validation is required");
    if (validation.isValid()) throw new IllegalArgumentException("Rejected publication requires issues");
  }

  /** All graph issues to return to the editor without changing publication state. */
  public GraphValidationResult validation() { return validation; }
}
