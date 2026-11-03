package com.uitopic.rumbo.planning.domain.model.entities;

import com.uitopic.rumbo.planning.domain.exceptions.InvalidMapVersionStateException;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapVersionStatus;
import java.util.Objects;
import java.util.UUID;

/** Lifecycle of one identifiable indoor-map graph version. */
public final class MapVersion {
  private final UUID id;
  private MapVersionStatus status;

  /** Reconstitutes a version in its stored state. */
  public MapVersion(UUID id, MapVersionStatus status) {
    this.id = Objects.requireNonNull(id, "id is required");
    this.status = Objects.requireNonNull(status, "status is required");
  }

  public UUID id() { return id; }
  public MapVersionStatus status() { return status; }

  /** Changes a validated draft into a published version. */
  public void publish() {
    if (status != MapVersionStatus.DRAFT) throw new InvalidMapVersionStateException(status);
    status = MapVersionStatus.PUBLISHED;
  }

  /** Retires a previously published version after its successor is ready. */
  public void archive() {
    if (status != MapVersionStatus.PUBLISHED) throw new InvalidMapVersionStateException(status);
    status = MapVersionStatus.ARCHIVED;
  }
}
