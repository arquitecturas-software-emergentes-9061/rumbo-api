package com.uitopic.rumbo.planning.application.internal.queryservices;

import com.uitopic.rumbo.planning.application.internal.exceptions.PlanningSiteNotFoundException;
import com.uitopic.rumbo.planning.domain.model.entities.Beacon;
import com.uitopic.rumbo.planning.domain.repositories.BeaconRepository;
import com.uitopic.rumbo.planning.domain.repositories.SiteRepository;
import com.uitopic.rumbo.planning.domain.services.BeaconQueryService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Restricts beacon reads to the site's owning account. */
@Service
public class BeaconQueryServiceImpl implements BeaconQueryService {
  private final SiteRepository sites;
  private final BeaconRepository beacons;

  public BeaconQueryServiceImpl(SiteRepository sites, BeaconRepository beacons) {
    this.sites = sites;
    this.beacons = beacons;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Beacon> list(UUID ownerUserId, UUID siteId, UUID floorId) {
    requireOwner(ownerUserId, siteId);
    return beacons.findBySite(siteId, floorId);
  }

  @Override
  @Transactional(readOnly = true)
  public Beacon get(UUID ownerUserId, UUID siteId, UUID beaconId) {
    requireOwner(ownerUserId, siteId);
    if (beaconId == null) throw new PlanningSiteNotFoundException();
    return beacons.findBySiteAndId(siteId, beaconId).orElseThrow(PlanningSiteNotFoundException::new);
  }

  private void requireOwner(UUID ownerUserId, UUID siteId) {
    if (ownerUserId == null) throw new IllegalArgumentException("Authenticated user required");
    if (!sites.isOwnedBy(siteId, ownerUserId)) throw new PlanningSiteNotFoundException();
  }
}
