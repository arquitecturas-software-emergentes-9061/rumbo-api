package com.uitopic.rumbo.planning.application.ports;

import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for draft indoor maps, independent of JPA and SQL. */
public interface IndoorMapStore {
  /** Saves the complete draft version as one unit. */
  void saveDraft(IndoorMap map);

  /** Loads a draft version and all of its plans, nodes, and connections. */
  Optional<IndoorMap> findDraft(UUID mapId, UUID modelVersionId);

  /** Locks and loads the draft so validation and publication see one coherent graph. */
  Optional<IndoorMap> findDraftForPublication(UUID mapId, UUID modelVersionId);

  /** Changes this validated version to published and archives the previous one atomically. */
  void publish(IndoorMap map);
}
