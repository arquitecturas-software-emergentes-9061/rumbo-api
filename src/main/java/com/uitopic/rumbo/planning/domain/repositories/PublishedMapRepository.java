package com.uitopic.rumbo.planning.domain.repositories;

import com.uitopic.rumbo.planning.domain.model.valueobjects.PublishedMapSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Planning-owned read contract exposing only the current published graph of one site. */
public interface PublishedMapRepository {
  /** Returns the current published snapshot, never a draft or archived version. */
  Optional<PublishedMapSnapshot> findBySite(UUID siteId);

  /** Searches indexed entries belonging to a site's current published version. */
  List<PublishedMapSnapshot.Destination> searchDestinations(UUID siteId, String term);
}
