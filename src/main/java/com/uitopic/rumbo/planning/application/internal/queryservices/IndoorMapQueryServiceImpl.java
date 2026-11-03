package com.uitopic.rumbo.planning.application.internal.queryservices;

import com.uitopic.rumbo.planning.application.internal.exceptions.MapVersionNotFoundException;
import com.uitopic.rumbo.planning.domain.repositories.IndoorMapStore;
import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import com.uitopic.rumbo.planning.domain.services.IndoorMapQueryService;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves draft graph reads without exposing the persistence adapter to REST. */
@Service
@Profile("postgres")
public class IndoorMapQueryServiceImpl implements IndoorMapQueryService {
  private final IndoorMapStore store;

  public IndoorMapQueryServiceImpl(IndoorMapStore store) {
    this.store = store;
  }

  @Override
  @Transactional(readOnly = true)
  public IndoorMap getDraft(UUID siteId, UUID mapId, UUID modelVersionId) {
    IndoorMap map = store.findDraft(mapId, modelVersionId)
        .orElseThrow(MapVersionNotFoundException::new);
    if (!map.siteId().equals(siteId)) throw new MapVersionNotFoundException();
    return map;
  }
}
