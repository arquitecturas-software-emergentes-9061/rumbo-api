package com.uitopic.rumbo.planning.application.internal.queryservices;

import com.uitopic.rumbo.planning.application.internal.exceptions.ScaleReferenceNotFoundException;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ScaleReference;
import com.uitopic.rumbo.planning.domain.repositories.ScaleReferenceRepository;
import com.uitopic.rumbo.planning.domain.repositories.SiteRepository;
import com.uitopic.rumbo.planning.domain.services.ScaleReferenceQueryService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads scale provenance only for the site's owning account. */
@Service
public class ScaleReferenceQueryServiceImpl implements ScaleReferenceQueryService {
  private final SiteRepository sites;
  private final ScaleReferenceRepository references;

  public ScaleReferenceQueryServiceImpl(SiteRepository sites, ScaleReferenceRepository references) {
    this.sites = sites;
    this.references = references;
  }

  @Override
  @Transactional(readOnly = true)
  public ScaleReference find(UUID ownerUserId, UUID siteId, UUID planVersionId) {
    if (!sites.isOwnedBy(siteId, ownerUserId)) throw new ScaleReferenceNotFoundException();
    return references.find(siteId, planVersionId).orElseThrow(ScaleReferenceNotFoundException::new);
  }
}
