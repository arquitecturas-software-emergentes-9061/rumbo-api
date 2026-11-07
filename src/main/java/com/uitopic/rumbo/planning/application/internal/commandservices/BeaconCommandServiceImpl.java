package com.uitopic.rumbo.planning.application.internal.commandservices;

import com.uitopic.rumbo.planning.application.internal.exceptions.InvalidBeaconAnchorException;
import com.uitopic.rumbo.planning.application.internal.exceptions.PlanningSiteNotFoundException;
import com.uitopic.rumbo.planning.domain.model.commands.SaveBeaconCommand;
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
  public Beacon register(UUID ownerUserId, UUID siteId, SaveBeaconCommand command) {
    requireOwner(ownerUserId, siteId);
    boolean placed = command.x() != null;
    Beacon beacon = build(UUID.randomUUID(), siteId, command, placed);
    requireAnchor(beacon);
    return beacons.create(beacon);
  }

  @Override
  @Transactional
  public Beacon update(UUID ownerUserId, UUID siteId, UUID beaconId, SaveBeaconCommand command) {
    requireOwner(ownerUserId, siteId);
    if (beaconId == null) throw new PlanningSiteNotFoundException();
    Beacon current = beacons.findBySiteAndId(siteId, beaconId)
        .orElseThrow(PlanningSiteNotFoundException::new);
    Beacon updated = build(beaconId, siteId, command, current.active() && command.x() != null,
        current.identifier());
    requireAnchor(updated);
    if (!beacons.update(updated)) throw new PlanningSiteNotFoundException();
    return updated;
  }

  @Override
  @Transactional
  public Beacon setActive(UUID ownerUserId, UUID siteId, UUID beaconId, boolean active) {
    requireOwner(ownerUserId, siteId);
    Beacon current = beaconId == null ? null : beacons.findBySiteAndId(siteId, beaconId).orElse(null);
    if (current == null) throw new PlanningSiteNotFoundException();
    if (active && !current.placed()) {
      throw new IllegalArgumentException("An unplaced beacon cannot be active");
    }
    if (!beacons.updateActive(siteId, beaconId, active)) throw new PlanningSiteNotFoundException();
    return beacons.findBySiteAndId(siteId, beaconId).orElseThrow(PlanningSiteNotFoundException::new);
  }

  private static Beacon build(UUID id, UUID siteId, SaveBeaconCommand command, boolean active) {
    return build(id, siteId, command, active, null);
  }

  private static Beacon build(UUID id, UUID siteId, SaveBeaconCommand command, boolean active,
                              String existingIdentifier) {
    String identifier = command.identifier() != null && !command.identifier().isBlank()
        ? command.identifier()
        : Beacon.deriveIdentifier(command.proximityUuid(), command.major(), command.minor());
    if (identifier == null) identifier = existingIdentifier;
    return new Beacon(id, siteId, command.floorId(), identifier, command.x(), command.y(), active,
        command.name(), command.proximityUuid(), command.major(), command.minor(),
        command.txPowerDbm());
  }

  private void requireAnchor(Beacon beacon) {
    if (beacon.placed() && !plans.containsPoint(beacon.siteId(), beacon.floorId(), beacon.x(), beacon.y())) {
      throw new InvalidBeaconAnchorException();
    }
  }

  private void requireOwner(UUID ownerUserId, UUID siteId) {
    if (ownerUserId == null) throw new IllegalArgumentException("Authenticated user required");
    if (!sites.isOwnedBy(siteId, ownerUserId)) throw new PlanningSiteNotFoundException();
  }
}
