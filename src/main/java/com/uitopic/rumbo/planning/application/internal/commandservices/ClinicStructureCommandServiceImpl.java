package com.uitopic.rumbo.planning.application.internal.commandservices;

import com.uitopic.rumbo.planning.application.internal.exceptions.PlanningSiteNotFoundException;
import com.uitopic.rumbo.planning.domain.model.aggregates.Clinic;
import com.uitopic.rumbo.planning.domain.model.entities.Floor;
import com.uitopic.rumbo.planning.domain.repositories.ClinicStructureRepository;
import com.uitopic.rumbo.planning.domain.repositories.SiteRepository;
import com.uitopic.rumbo.planning.domain.services.ClinicStructureCommandService;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicStructureCommandServiceImpl implements ClinicStructureCommandService {
  private final ClinicStructureRepository structure;
  private final SiteRepository sites;
  public ClinicStructureCommandServiceImpl(ClinicStructureRepository structure, SiteRepository sites) {
    this.structure = structure; this.sites = sites;
  }

  @Override @Transactional
  public Clinic saveClinic(UUID owner, String name, String code, String email, String phone) {
    Objects.requireNonNull(owner, "Authenticated owner required");
    UUID id = structure.findClinicByOwner(owner).map(Clinic::id).orElseGet(UUID::randomUUID);
    return structure.saveClinic(owner, new Clinic(id, name, code, email, phone));
  }

  @Override @Transactional
  public Floor createFloor(UUID owner, UUID siteId, int number, String name, String description) {
    if (!sites.isOwnedBy(siteId, owner)) throw new PlanningSiteNotFoundException();
    return structure.createFloor(new Floor(UUID.randomUUID(), siteId, number, name, description));
  }
}
