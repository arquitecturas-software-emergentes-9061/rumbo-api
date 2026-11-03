package com.uitopic.rumbo.planning.application.internal.commandservices;

import com.uitopic.rumbo.planning.application.exceptions.MapVersionNotFoundException;
import com.uitopic.rumbo.planning.application.ports.IndoorMapStore;
import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import com.uitopic.rumbo.planning.domain.model.commands.PublishMapVersionCommand;
import com.uitopic.rumbo.planning.domain.services.IndoorMapCommandService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates locked retrieval, domain validation, and atomic publication. */
@Service
@Profile("postgres")
public class IndoorMapCommandServiceImpl implements IndoorMapCommandService {
  private final IndoorMapStore store;

  public IndoorMapCommandServiceImpl(IndoorMapStore store) {
    this.store = store;
  }

  @Override
  @Transactional
  public IndoorMap handle(PublishMapVersionCommand command) {
    IndoorMap map = store.findDraftForPublication(command.mapId(), command.modelVersionId())
        .orElseThrow(MapVersionNotFoundException::new);
    if (!map.siteId().equals(command.siteId())) throw new MapVersionNotFoundException();
    map.publish();
    store.publish(map);
    return map;
  }
}
