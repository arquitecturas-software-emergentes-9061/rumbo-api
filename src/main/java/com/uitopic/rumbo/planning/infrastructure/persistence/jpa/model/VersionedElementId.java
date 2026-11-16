package com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** SQL identity of an element scoped to one indoor-model version. */
@Embeddable
public class VersionedElementId implements Serializable {
  @Column(name = "id", nullable = false)
  private UUID elementId;

  @Column(name = "model_version_id", nullable = false)
  private UUID modelVersionId;

  protected VersionedElementId() {}

  public VersionedElementId(UUID elementId, UUID modelVersionId) {
    this.elementId = Objects.requireNonNull(elementId, "elementId is required");
    this.modelVersionId = Objects.requireNonNull(modelVersionId, "modelVersionId is required");
  }

  public UUID elementId() { return elementId; }
  public UUID modelVersionId() { return modelVersionId; }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof VersionedElementId id)) return false;
    return elementId.equals(id.elementId) && modelVersionId.equals(id.modelVersionId);
  }

  @Override
  public int hashCode() { return Objects.hash(elementId, modelVersionId); }
}
