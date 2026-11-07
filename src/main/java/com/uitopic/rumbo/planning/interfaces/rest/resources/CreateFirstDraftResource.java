package com.uitopic.rumbo.planning.interfaces.rest.resources;

import com.uitopic.rumbo.planning.domain.model.commands.CreateFirstDraftMapCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Selects the stored floor-plan versions that make up a site's first draft. */
public record CreateFirstDraftResource(
    @NotEmpty @Size(max = 50) List<@NotNull @Valid PlanResource> plans) {
  /** One floor and the floor-plan version drawn on it. */
  public record PlanResource(@NotNull UUID floorId, @NotNull UUID planVersionId) {}

  public CreateFirstDraftMapCommand toCommand(UUID siteId) {
    return new CreateFirstDraftMapCommand(siteId, plans.stream()
        .map(plan -> new CreateFirstDraftMapCommand.PlanSource(plan.floorId(), plan.planVersionId())).toList());
  }
}
