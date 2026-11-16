package com.uitopic.rumbo.planning.domain.services;

import com.uitopic.rumbo.planning.domain.model.entities.Site;
import java.util.UUID;

/** Creates clinic sites owned by the authenticated account. */
public interface SiteCommandService {
  Site create(UUID ownerUserId, String name);
  Site create(UUID ownerUserId, String name, String address, boolean allowDuplicateAddress);
  Site update(UUID ownerUserId, UUID siteId, String name, String address, boolean allowDuplicateAddress);
}
