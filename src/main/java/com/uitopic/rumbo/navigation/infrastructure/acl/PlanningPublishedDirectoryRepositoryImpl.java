package com.uitopic.rumbo.navigation.infrastructure.acl;

import com.uitopic.rumbo.navigation.domain.model.valueobjects.DirectoryEntry;
import com.uitopic.rumbo.navigation.domain.repositories.PublishedDirectoryRepository;
import com.uitopic.rumbo.planning.interfaces.acl.PlanningPublishedMapFacade;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** Translates Planning's published directory contract at Navigation's ACL. */
@Repository
@Profile("postgres")
public class PlanningPublishedDirectoryRepositoryImpl implements PublishedDirectoryRepository {
  private final PlanningPublishedMapFacade planning;

  public PlanningPublishedDirectoryRepositoryImpl(PlanningPublishedMapFacade planning) {
    this.planning = planning;
  }

  @Override
  public List<DirectoryEntry> search(UUID siteId, String term) {
    return planning.searchCurrentDestinations(siteId, term).stream()
        .map(entry -> new DirectoryEntry(entry.id(), siteId, entry.modelVersionId(),
            entry.floorId(), entry.nodeId(), DirectoryEntry.EntryType.valueOf(entry.type()),
            entry.name(), entry.specialty())).toList();
  }
}
