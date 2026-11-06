package com.uitopic.rumbo.planning.domain.repositories;

import com.uitopic.rumbo.planning.domain.model.aggregates.Clinic;
import com.uitopic.rumbo.planning.domain.model.entities.Floor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for administrative structure, distinct from graph-version storage. */
public interface ClinicStructureRepository {
  Optional<Clinic> findClinicByOwner(UUID ownerUserId);
  Clinic saveClinic(UUID ownerUserId, Clinic clinic);
  List<Floor> findFloors(UUID siteId);
  Floor createFloor(Floor floor);
}
