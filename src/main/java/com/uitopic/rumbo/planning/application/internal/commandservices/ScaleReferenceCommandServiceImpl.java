package com.uitopic.rumbo.planning.application.internal.commandservices;

import com.uitopic.rumbo.planning.application.internal.exceptions.ScaleReferenceFloorConflictException;
import com.uitopic.rumbo.planning.application.internal.exceptions.ScaleReferenceNotFoundException;
import com.uitopic.rumbo.planning.application.internal.FloorPlanVersionService;
import com.uitopic.rumbo.planning.domain.exceptions.InvalidFloorPlanFile;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ScaleReference;
import com.uitopic.rumbo.planning.domain.repositories.PlanSpatialReferenceRepository;
import com.uitopic.rumbo.planning.domain.repositories.ScaleReferenceRepository;
import com.uitopic.rumbo.planning.domain.repositories.SiteRepository;
import com.uitopic.rumbo.planning.domain.services.ScaleReferenceCommandService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates ownership, floor identity and bounds before atomically saving calibration. */
@Service
public class ScaleReferenceCommandServiceImpl implements ScaleReferenceCommandService {
  private final SiteRepository sites;
  private final PlanSpatialReferenceRepository plans;
  private final ScaleReferenceRepository references;
  private final FloorPlanVersionService originals;

  public ScaleReferenceCommandServiceImpl(SiteRepository sites,
      PlanSpatialReferenceRepository plans, ScaleReferenceRepository references,
      FloorPlanVersionService originals) {
    this.sites = sites;
    this.plans = plans;
    this.references = references;
    this.originals = originals;
  }

  @Override
  @Transactional
  public ScaleReference upsert(UUID ownerUserId, ScaleReference reference) {
    if (!sites.isOwnedBy(reference.siteId(), ownerUserId)) throw new ScaleReferenceNotFoundException();
    if (references.existsOnDifferentFloor(reference.siteId(), reference.planVersionId(),
        reference.floorId())) throw new ScaleReferenceFloorConflictException();
    var draft = plans.lockDraftPlan(reference.siteId(), reference.floorId(), reference.planVersionId());
    if (draft.isPresent()) {
      var bounds = draft.get();
      requireInside(bounds.contains(reference));
      return references.upsert(reference, bounds.modelVersionId())
        .orElseThrow(ScaleReferenceFloorConflictException::new);
    }
    if (plans.existsInGraph(reference.siteId(), reference.planVersionId())) {
      throw new ScaleReferenceNotFoundException();
    }
    try {
      var original = originals.original(ownerUserId, reference.siteId(),
        reference.floorId(), reference.planVersionId());
      requireInside(originals.bounds(original).contains(reference));
    } catch (FloorPlanVersionService.NotFound exception) {
      throw new ScaleReferenceNotFoundException();
    } catch (InvalidFloorPlanFile exception) {
      throw new IllegalArgumentException("The original plan has no usable dimensions", exception);
    }
    return references.upsertOriginal(reference).orElseThrow(ScaleReferenceFloorConflictException::new);
  }

  private static void requireInside(boolean inside) {
    if (!inside)
      throw new IllegalArgumentException("Calibration points are outside the referenced plan");
  }
}
