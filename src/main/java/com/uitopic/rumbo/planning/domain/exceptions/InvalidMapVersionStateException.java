package com.uitopic.rumbo.planning.domain.exceptions;

import com.uitopic.rumbo.planning.domain.model.valueobjects.MapVersionStatus;
import java.util.Objects;

/** An edit or publication was attempted from a non-draft map version. */
public final class InvalidMapVersionStateException extends IllegalStateException {
  private final MapVersionStatus status;

  public InvalidMapVersionStateException(MapVersionStatus status) {
    super("Indoor map version must be DRAFT; current status is " + status);
    this.status = Objects.requireNonNull(status, "status is required");
  }

  /** State that prevented the requested transition. */
  public MapVersionStatus status() { return status; }
}
