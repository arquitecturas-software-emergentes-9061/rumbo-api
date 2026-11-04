package com.uitopic.rumbo.planning.application.internal.commandservices;

import com.uitopic.rumbo.planning.application.internal.exceptions.InvalidBeaconAnchorException;
import com.uitopic.rumbo.planning.application.internal.exceptions.PlanningSiteNotFoundException;
import com.uitopic.rumbo.planning.domain.model.entities.Beacon;
import com.uitopic.rumbo.planning.domain.repositories.BeaconRepository;
import com.uitopic.rumbo.planning.domain.repositories.PlanSpatialReferenceRepository;
import com.uitopic.rumbo.planning.domain.repositories.SiteRepository;
import com.uitopic.rumbo.planning.domain.services.BeaconCommandService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Enforces site ownership and plan geometry before changing beacon inventory. */
@Service
public class BeaconCommandServiceImpl implements BeaconCommandService {
  private final SiteRepository sites;
  private final PlanSpatialReferenceRepository plans;
  private final BeaconRepository beacons;

  public BeaconCommandServiceImpl(SiteRepository sites, PlanSpatialReferenceRepository plans,
                                  BeaconRepository beacons) {
    this.sites = sites;
    this.plans = plans;
    this.beacons = beacons;
  }

  @Override
  @Transactional
  public Beacon register(UUID ownerUserId, UUID siteId, UUID floorId,
                         String identifier, double xUnits, double yUnits) {
    requireOwner(ownerUserId, siteId);
    Beacon beacon = new Beacon(UUID.randomUUID(), siteId, floorId, identifier,
        xUnits, yUnits, true);
    if (!plans.containsPoint(siteId, floorId, xUnits, yUnits))
      throw new InvalidBeaconAnchorException();
    return beacons.create(beacon);
  }

  @Override
  @Transactional
  public Beacon setActive(UUID ownerUserId, UUID siteId, UUID beaconId, boolean active) {
    requireOwner(ownerUserId, siteId);
    if (beaconId == null || !beacons.updateActive(siteId, beaconId, active))
      throw new PlanningSiteNotFoundException();
    return beacons.findBySiteAndId(siteId, beaconId).orElseThrow(PlanningSiteNotFoundException::new);
  }

  private void requireOwner(UUID ownerUserId, UUID siteId) {
    if (ownerUserId == null) throw new IllegalArgumentException("Authenticated user required");
    if (!sites.isOwnedBy(siteId, ownerUserId)) throw new PlanningSiteNotFoundException();
  }
}
