package com.uitopic.rumbo.planning.application.internal.queryservices;

import com.uitopic.rumbo.planning.domain.model.entities.Site;
import com.uitopic.rumbo.planning.domain.repositories.SiteRepository;
import com.uitopic.rumbo.planning.domain.services.SiteQueryService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Limits site queries to the authenticated owner. */
@Service
public class SiteQueryServiceImpl implements SiteQueryService {
  private final SiteRepository sites;

  public SiteQueryServiceImpl(SiteRepository sites) { this.sites = sites; }

  @Override
  @Transactional(readOnly = true)
  public List<Site> findByOwner(UUID ownerUserId) {
    if (ownerUserId == null) throw new IllegalArgumentException("Authenticated user required");
    return sites.findByOwner(ownerUserId);
  }
}
