package com.uitopic.rumbo.planning.domain.services;

import com.uitopic.rumbo.planning.domain.model.aggregates.Clinic;
import com.uitopic.rumbo.planning.domain.model.entities.Floor;
import java.util.UUID;

public interface ClinicStructureCommandService {
  Clinic saveClinic(UUID owner, String name, String code, String email, String phone);
  Floor createFloor(UUID owner, UUID siteId, int number, String name, String description);
}
