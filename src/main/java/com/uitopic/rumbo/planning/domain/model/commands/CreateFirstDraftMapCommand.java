package com.uitopic.rumbo.planning.domain.model.commands;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Requests the first editable draft of a site that has no indoor map yet. Each plan names a stored
 * floor-plan version; its dimensions and confirmed scale are read from Planning, never from the caller.
 */
public record CreateFirstDraftMapCommand(UUID siteId, List<PlanSource> plans) {
  public CreateFirstDraftMapCommand {
    Objects.requireNonNull(siteId, "siteId is required");
    Objects.requireNonNull(plans, "plans is required");
    plans = List.copyOf(plans);
  }

  /** One stored floor-plan version that becomes a coordinate space of the draft. */
  public record PlanSource(UUID floorId, UUID planVersionId) {
    public PlanSource {
      Objects.requireNonNull(floorId, "floorId is required");
      Objects.requireNonNull(planVersionId, "planVersionId is required");
    }
  }
}
