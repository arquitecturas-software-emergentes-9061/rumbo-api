package com.uitopic.rumbo.planning.domain.repositories;

import com.uitopic.rumbo.planning.domain.model.entities.Site;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for clinic sites and their current account ownership. */
public interface SiteRepository {
  Site create(UUID ownerUserId, Site site);
  Site update(UUID ownerUserId, Site site);
  List<Site> findByOwner(UUID ownerUserId);
  boolean isOwnedBy(UUID siteId, UUID ownerUserId);

  /** Current owner of the site, empty if it does not exist. */
  Optional<UUID> findOwnerOf(UUID siteId);
}
