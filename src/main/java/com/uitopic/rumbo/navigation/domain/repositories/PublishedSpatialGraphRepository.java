package com.uitopic.rumbo.navigation.domain.repositories;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedSpatialGraph;


import java.util.Optional;
import java.util.UUID;

/** Read-only port for the current published graph of a site. */
public interface PublishedSpatialGraphRepository {
  /** Never returns a draft or archived version. */
  Optional<PublishedSpatialGraph> findCurrentBySite(UUID siteId);

  /** Checks only current published versions when an ID is absent from the requested site. */
  boolean existsCurrentNodeOutsideSite(UUID nodeId, UUID siteId);
}
