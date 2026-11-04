package com.uitopic.rumbo.planning.domain.repositories;

import com.uitopic.rumbo.planning.domain.model.entities.Beacon;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for registered beacon inventory, without radio observations. */
public interface BeaconRepository {
  Beacon create(Beacon beacon);
  List<Beacon> findBySite(UUID siteId, UUID floorId);
  Optional<Beacon> findBySiteAndId(UUID siteId, UUID beaconId);
  boolean updateActive(UUID siteId, UUID beaconId, boolean active);
}
