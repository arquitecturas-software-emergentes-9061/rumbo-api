package com.uitopic.rumbo.planning.domain.services;

import com.uitopic.rumbo.planning.domain.model.aggregates.Clinic;
import com.uitopic.rumbo.planning.domain.model.entities.Floor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicStructureQueryService {
  Optional<Clinic> findClinic(UUID owner);
  List<Floor> findFloors(UUID owner, UUID siteId);
}
