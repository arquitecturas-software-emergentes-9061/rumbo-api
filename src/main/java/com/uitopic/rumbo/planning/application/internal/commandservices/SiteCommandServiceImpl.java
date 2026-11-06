package com.uitopic.rumbo.planning.application.internal.commandservices;

import com.uitopic.rumbo.planning.domain.model.entities.Site;
import com.uitopic.rumbo.planning.domain.repositories.SiteRepository;
import com.uitopic.rumbo.planning.domain.repositories.ClinicStructureRepository;
import com.uitopic.rumbo.planning.domain.model.aggregates.Clinic;
import com.uitopic.rumbo.planning.domain.exceptions.ClinicStructureConflictException;
import com.uitopic.rumbo.planning.application.internal.exceptions.PlanningSiteNotFoundException;
import com.uitopic.rumbo.planning.domain.services.SiteCommandService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates creation of a site and its initial owner. */
@Service
public class SiteCommandServiceImpl implements SiteCommandService {
  private final SiteRepository sites;
  private final ClinicStructureRepository structure;

  public SiteCommandServiceImpl(SiteRepository sites, ClinicStructureRepository structure) {
    this.sites = sites; this.structure = structure;
  }

  @Override
  @Transactional
  public Site create(UUID ownerUserId, String name) {
    return create(ownerUserId, name, null, false);
  }

  @Override @Transactional
  public Site create(UUID ownerUserId, String name, String address, boolean allowDuplicateAddress) {
    if (ownerUserId == null) throw new IllegalArgumentException("Authenticated user required");
    Site site = new Site(UUID.randomUUID(), name, address,
        structure.findClinicByOwner(ownerUserId).map(Clinic::id).orElse(null));
    checkAddress(ownerUserId, site, allowDuplicateAddress);
    return sites.create(ownerUserId, site);
  }

  @Override @Transactional
  public Site update(UUID ownerUserId, UUID siteId, String name, String address, boolean allowDuplicateAddress) {
    Site existing = sites.findByOwner(ownerUserId).stream().filter(s -> s.id().equals(siteId)).findFirst()
        .orElseThrow(PlanningSiteNotFoundException::new);
    Site updated = new Site(siteId, name, address, existing.clinicId());
    checkAddress(ownerUserId, updated, allowDuplicateAddress);
    return sites.update(ownerUserId, updated);
  }

  private void checkAddress(UUID owner, Site site, boolean acknowledged) {
    String normalized = Site.normalizedAddress(site.address());
    if (!acknowledged && !normalized.isEmpty() && sites.findByOwner(owner).stream()
        .anyMatch(existing -> !existing.id().equals(site.id())
          && normalized.equals(Site.normalizedAddress(existing.address())))) {
      throw new ClinicStructureConflictException("SITE_ADDRESS_EXISTS",
          "Another site in your clinic has the same address; review it or explicitly acknowledge the coincidence");
    }
  }
}
