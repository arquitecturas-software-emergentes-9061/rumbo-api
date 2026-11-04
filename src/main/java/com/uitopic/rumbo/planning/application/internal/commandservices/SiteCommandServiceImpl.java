package com.uitopic.rumbo.planning.application.internal.commandservices;

import com.uitopic.rumbo.planning.domain.model.entities.Site;
import com.uitopic.rumbo.planning.domain.repositories.SiteRepository;
import com.uitopic.rumbo.planning.domain.services.SiteCommandService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates creation of a site and its initial owner. */
@Service
public class SiteCommandServiceImpl implements SiteCommandService {
  private final SiteRepository sites;

  public SiteCommandServiceImpl(SiteRepository sites) { this.sites = sites; }

  @Override
  @Transactional
  public Site create(UUID ownerUserId, String name) {
    if (ownerUserId == null) throw new IllegalArgumentException("Authenticated user required");
    return sites.create(ownerUserId, new Site(UUID.randomUUID(), name));
  }
}
