package com.uitopic.rumbo.planning.application.acl;

import com.uitopic.rumbo.planning.interfaces.acl.PlanningClinicFacade;
import com.uitopic.rumbo.planning.domain.services.ClinicStructureQueryService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PlanningClinicFacadeImpl implements PlanningClinicFacade {
  private final ClinicStructureQueryService clinics;
  public PlanningClinicFacadeImpl(ClinicStructureQueryService clinics) { this.clinics = clinics; }
  @Override
  public Optional<ClinicSummary> findClinicOwnedBy(UUID ownerUserId) {
    return clinics.findClinic(ownerUserId).map(clinic -> new ClinicSummary(clinic.id(), clinic.name()));
  }
}
