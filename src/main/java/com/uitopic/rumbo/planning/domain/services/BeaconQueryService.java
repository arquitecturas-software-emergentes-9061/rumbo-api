package com.uitopic.rumbo.planning.domain.services;

import com.uitopic.rumbo.planning.domain.model.entities.Beacon;
import java.util.List;
import java.util.UUID;

/** Reads the beacon inventory of an owned site. */
public interface BeaconQueryService {
  List<Beacon> list(UUID ownerUserId, UUID siteId, UUID floorId);
  Beacon get(UUID ownerUserId, UUID siteId, UUID beaconId);
}
