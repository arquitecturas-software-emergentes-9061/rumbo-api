package com.uitopic.rumbo.planning.domain.repositories;

import com.uitopic.rumbo.planning.domain.model.valueobjects.PublishedMapSnapshot;
import java.util.Optional;
import java.util.UUID;

/** Planning-owned read contract exposing only the current published graph of one site. */
public interface PublishedMapReader {
  /** Returns the current published snapshot, never a draft or archived version. */
  Optional<PublishedMapSnapshot> findBySite(UUID siteId);
}
