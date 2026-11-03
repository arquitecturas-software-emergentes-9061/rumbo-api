package com.uitopic.rumbo.navigation.application;

import com.uitopic.rumbo.navigation.domain.PublishedSpatialGraph;
import com.uitopic.rumbo.navigation.domain.PublishedSpatialGraphRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Navigation use case for reading one currently published spatial graph. */
@Service
@Profile("postgres")
public class GetPublishedSpatialGraphQueryHandler {
  private final PublishedSpatialGraphRepository repository;

  public GetPublishedSpatialGraphQueryHandler(PublishedSpatialGraphRepository repository) {
    this.repository = repository;
  }

  /** Returns no graph when a site has no published version. */
  public Optional<PublishedSpatialGraph> handle(UUID siteId) {
    return repository.findCurrentBySite(Objects.requireNonNull(siteId, "siteId is required"));
  }
}
