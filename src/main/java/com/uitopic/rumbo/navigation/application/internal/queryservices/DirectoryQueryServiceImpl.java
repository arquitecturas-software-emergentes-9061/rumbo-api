package com.uitopic.rumbo.navigation.application.internal.queryservices;

import com.uitopic.rumbo.navigation.domain.model.valueobjects.DirectoryEntry;
import com.uitopic.rumbo.navigation.domain.repositories.PublishedDirectoryRepository;
import com.uitopic.rumbo.navigation.domain.services.DirectoryQueryService;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Coordinates site-scoped directory search without exposing Planning to REST. */
@Service
@Profile("postgres")
public class DirectoryQueryServiceImpl implements DirectoryQueryService {
  private final PublishedDirectoryRepository repository;

  public DirectoryQueryServiceImpl(PublishedDirectoryRepository repository) {
    this.repository = repository;
  }

  /** Searches a non-empty term in the current published model of one site. */
  @Override
  public List<DirectoryEntry> search(UUID siteId, String term) {
    Objects.requireNonNull(siteId, "siteId is required");
    if (term == null || term.isBlank() || term.length() > 150) {
      throw new IllegalArgumentException("q must contain 1 to 150 characters");
    }
    return repository.search(siteId, term.strip());
  }
}
