package com.uitopic.rumbo.planning.domain.services;

import com.uitopic.rumbo.planning.domain.model.entities.Site;
import java.util.List;
import java.util.UUID;

/** Reads only sites owned by the authenticated account. */
public interface SiteQueryService {
  List<Site> findByOwner(UUID ownerUserId);
}
