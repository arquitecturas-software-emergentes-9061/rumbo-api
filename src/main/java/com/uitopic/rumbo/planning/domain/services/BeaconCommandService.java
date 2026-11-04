package com.uitopic.rumbo.planning.domain.services;

import com.uitopic.rumbo.planning.domain.model.entities.Beacon;
import java.util.UUID;

/** Registers beacons and changes their active state within an owned site. */
public interface BeaconCommandService {
  Beacon register(UUID ownerUserId, UUID siteId, UUID floorId,
                  String identifier, double xUnits, double yUnits);
  Beacon setActive(UUID ownerUserId, UUID siteId, UUID beaconId, boolean active);
}
