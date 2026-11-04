package com.uitopic.rumbo.planning.application.internal.commandservices;

import com.uitopic.rumbo.planning.application.internal.exceptions.ScaleReferenceFloorConflictException;
import com.uitopic.rumbo.planning.application.internal.exceptions.ScaleReferenceNotFoundException;
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

  public ScaleReferenceCommandServiceImpl(SiteRepository sites,
      PlanSpatialReferenceRepository plans, ScaleReferenceRepository references) {
    this.sites = sites;
    this.plans = plans;
    this.references = references;
  }

  @Override
  @Transactional
  public ScaleReference upsert(UUID ownerUserId, ScaleReference reference) {
    if (!sites.isOwnedBy(reference.siteId(), ownerUserId)) throw new ScaleReferenceNotFoundException();
    if (references.existsOnDifferentFloor(reference.siteId(), reference.planVersionId(),
        reference.floorId())) throw new ScaleReferenceFloorConflictException();
    var bounds = plans.lockDraftPlan(reference.siteId(), reference.floorId(),
        reference.planVersionId()).orElseThrow(ScaleReferenceNotFoundException::new);
    if (!bounds.contains(reference))
      throw new IllegalArgumentException("Calibration points are outside the referenced plan");
    return references.upsert(reference, bounds.modelVersionId())
        .orElseThrow(ScaleReferenceFloorConflictException::new);
  }
}
