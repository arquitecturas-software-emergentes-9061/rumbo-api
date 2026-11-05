package com.uitopic.rumbo.planning.application.internal.queryservices;

import com.uitopic.rumbo.planning.application.internal.exceptions.PlanningSiteNotFoundException;
import com.uitopic.rumbo.planning.domain.model.aggregates.Clinic;
import com.uitopic.rumbo.planning.domain.model.entities.Floor;
import com.uitopic.rumbo.planning.domain.repositories.ClinicStructureRepository;
import com.uitopic.rumbo.planning.domain.repositories.SiteRepository;
import com.uitopic.rumbo.planning.domain.services.ClinicStructureQueryService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class ClinicStructureQueryServiceImpl implements ClinicStructureQueryService {
  private final ClinicStructureRepository structure;
  private final SiteRepository sites;
  public ClinicStructureQueryServiceImpl(ClinicStructureRepository structure, SiteRepository sites) {
    this.structure = structure; this.sites = sites;
  }
  @Override public Optional<Clinic> findClinic(UUID owner) {
    return structure.findClinicByOwner(Objects.requireNonNull(owner, "Authenticated owner required"));
  }
  @Override public List<Floor> findFloors(UUID owner, UUID siteId) {
    if (!sites.isOwnedBy(siteId, owner)) throw new PlanningSiteNotFoundException();
    return structure.findFloors(siteId);
  }
}
