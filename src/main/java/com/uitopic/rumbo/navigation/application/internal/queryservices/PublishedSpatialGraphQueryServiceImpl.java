package com.uitopic.rumbo.navigation.application.internal.queryservices;

import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedSpatialGraph;
import com.uitopic.rumbo.navigation.domain.repositories.PublishedSpatialGraphRepository;
import com.uitopic.rumbo.navigation.domain.services.PublishedSpatialGraphQueryService;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Navigation use case for reading one currently published spatial graph. */
@Service
@Profile("postgres")
public class PublishedSpatialGraphQueryServiceImpl implements PublishedSpatialGraphQueryService {
  private final PublishedSpatialGraphRepository repository;

  public PublishedSpatialGraphQueryServiceImpl(PublishedSpatialGraphRepository repository) {
    this.repository = repository;
  }

  /** Returns no graph when a site has no published version. */
  @Override
  public Optional<PublishedSpatialGraph> findCurrentBySite(UUID siteId) {
    return repository.findCurrentBySite(Objects.requireNonNull(siteId, "siteId is required"));
  }
}
