package com.uitopic.rumbo.planning.domain.repositories;

import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for draft indoor maps, independent of JPA and SQL. */
public interface IndoorMapRepository {
  /** Serializes lifecycle changes for an existing map of the requested site. */
  boolean lockMapForUpdate(UUID siteId, UUID mapId);

  /** Checks for an editable version while the owning map is locked. */
  boolean hasDraft(UUID mapId);

  /** Saves the complete draft version as one unit. */
  void saveDraft(IndoorMap map);

  /** Loads a draft version and all of its plans, nodes, and connections. */
  Optional<IndoorMap> findDraft(UUID mapId, UUID modelVersionId);

  /** Locks and loads the draft so an edit or publication sees one coherent graph. */
  Optional<IndoorMap> findDraftForUpdate(UUID mapId, UUID modelVersionId);

  /** Changes this validated version to published and archives the previous one atomically. */
  void publish(IndoorMap map);
}
