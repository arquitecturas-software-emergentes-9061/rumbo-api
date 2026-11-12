package com.uitopic.rumbo.subscriptions.infrastructure.acl;

import com.uitopic.rumbo.planning.interfaces.acl.PlanningClinicFacade;
import com.uitopic.rumbo.subscriptions.application.ports.OrganizationDirectory;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.OrganizationRef;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PlanningOrganizationDirectory implements OrganizationDirectory {
  private final PlanningClinicFacade clinics;
  public PlanningOrganizationDirectory(PlanningClinicFacade clinics) { this.clinics = clinics; }
  @Override
  public Optional<OrganizationRef> findOrganizationOf(UUID userId) {
    return clinics.findClinicOwnedBy(userId).map(clinic -> new OrganizationRef(clinic.id(), clinic.name()));
  }
}
