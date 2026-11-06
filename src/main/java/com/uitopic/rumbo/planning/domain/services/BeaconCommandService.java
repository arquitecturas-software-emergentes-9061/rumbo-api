package com.uitopic.rumbo.planning.domain.services;

import com.uitopic.rumbo.planning.domain.model.commands.SaveBeaconCommand;
import com.uitopic.rumbo.planning.domain.model.entities.Beacon;
import java.util.UUID;

/** Registers, edits and activates beacons within an owned site. */
public interface BeaconCommandService {
  Beacon register(UUID ownerUserId, UUID siteId, SaveBeaconCommand command);
  Beacon update(UUID ownerUserId, UUID siteId, UUID beaconId, SaveBeaconCommand command);
  Beacon setActive(UUID ownerUserId, UUID siteId, UUID beaconId, boolean active);
}
