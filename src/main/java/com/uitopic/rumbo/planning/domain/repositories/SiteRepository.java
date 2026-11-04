package com.uitopic.rumbo.planning.domain.repositories;

import com.uitopic.rumbo.planning.domain.model.entities.Site;
import java.util.List;
import java.util.UUID;

/** Persistence boundary for clinic sites and their current account ownership. */
public interface SiteRepository {
  Site create(UUID ownerUserId, Site site);
  List<Site> findByOwner(UUID ownerUserId);
  boolean isOwnedBy(UUID siteId, UUID ownerUserId);
}
