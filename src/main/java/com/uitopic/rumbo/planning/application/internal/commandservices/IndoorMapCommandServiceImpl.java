package com.uitopic.rumbo.planning.application.internal.commandservices;

import com.uitopic.rumbo.planning.application.internal.exceptions.MapVersionNotFoundException;
import com.uitopic.rumbo.planning.domain.exceptions.DraftGraphRevisionConflictException;
import com.uitopic.rumbo.planning.domain.repositories.IndoorMapRepository;
import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import com.uitopic.rumbo.planning.domain.model.commands.PublishMapVersionCommand;
import com.uitopic.rumbo.planning.domain.model.commands.ReplaceDraftGraphCommand;
import com.uitopic.rumbo.planning.domain.services.IndoorMapCommandService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates locked retrieval, domain validation, and atomic publication. */
@Service
@Profile("postgres")
public class IndoorMapCommandServiceImpl implements IndoorMapCommandService {
  private final IndoorMapRepository repository;

  public IndoorMapCommandServiceImpl(IndoorMapRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional
  public IndoorMap handle(PublishMapVersionCommand command) {
    IndoorMap map = repository.findDraftForUpdate(command.mapId(), command.modelVersionId())
        .orElseThrow(MapVersionNotFoundException::new);
    if (!map.siteId().equals(command.siteId())) throw new MapVersionNotFoundException();
    map.publish();
    repository.publish(map);
    return map;
  }

  @Override
  @Transactional
  public IndoorMap handle(ReplaceDraftGraphCommand command) {
    IndoorMap current = repository.findDraftForUpdate(command.mapId(), command.modelVersionId())
        .orElseThrow(MapVersionNotFoundException::new);
    if (!current.siteId().equals(command.siteId())) throw new MapVersionNotFoundException();
    if (current.editRevision() != command.expectedRevision()) {
      throw new DraftGraphRevisionConflictException();
    }

    IndoorMap replacement = new IndoorMap(current.id(), current.siteId(),
        current.modelVersionId(), current.editRevision());
    current.plans().forEach(replacement::addPlan);
    current.scales().forEach(replacement::setScale);
    command.nodes().forEach(node -> replacement.addNode(node.id(), node.floorId(),
        node.planVersionId(), node.x(), node.y(), node.type()));
    command.connections().forEach(connection -> replacement.addConnection(connection.id(),
        connection.sourceNodeId(), connection.targetNodeId(), connection.type(),
        connection.distanceMeters(), connection.weight(), connection.verticalPenalty(),
        connection.accessibility(), connection.bidirectional()));
    command.destinations().forEach(destination -> replacement.addDestination(destination.id(),
        destination.nodeId(), destination.type(), destination.name(), destination.specialty()));
    repository.saveDraft(replacement);
    return replacement;
  }
}
