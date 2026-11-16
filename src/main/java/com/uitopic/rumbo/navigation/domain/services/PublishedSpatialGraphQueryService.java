package com.uitopic.rumbo.navigation.domain.services;

import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedSpatialGraph;
import java.util.Optional;
import java.util.UUID;

/** Reads the current published spatial model without exposing Planning internals. */
public interface PublishedSpatialGraphQueryService {
  Optional<PublishedSpatialGraph> findCurrentBySite(UUID siteId);
}
