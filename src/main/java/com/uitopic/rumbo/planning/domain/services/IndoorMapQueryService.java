package com.uitopic.rumbo.planning.domain.services;

import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import java.util.UUID;

/** Read use cases for editable Planning map versions. */
public interface IndoorMapQueryService {
  /** Returns an existing draft graph that belongs to the requested site. */
  IndoorMap getDraft(UUID siteId, UUID mapId, UUID modelVersionId);
}
