package com.uitopic.rumbo.planning.application.internal.commandservices;

import com.uitopic.rumbo.planning.application.internal.exceptions.MapVersionNotFoundException;
import com.uitopic.rumbo.planning.application.internal.exceptions.DraftMapVersionAlreadyExistsException;
import com.uitopic.rumbo.planning.application.internal.exceptions.PublishedMapVersionChangedException;
import com.uitopic.rumbo.planning.application.internal.exceptions.InvalidPublishedMapVersionException;
import com.uitopic.rumbo.planning.domain.exceptions.DraftGraphRevisionConflictException;
import com.uitopic.rumbo.planning.domain.exceptions.InvalidPublishedMapDataException;
import com.uitopic.rumbo.planning.domain.repositories.IndoorMapRepository;
import com.uitopic.rumbo.planning.domain.repositories.PublishedMapRepository;
import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import com.uitopic.rumbo.planning.domain.model.commands.PublishMapVersionCommand;
import com.uitopic.rumbo.planning.domain.model.commands.CreateDraftMapVersionCommand;
import com.uitopic.rumbo.planning.domain.model.commands.ReplaceDraftGraphCommand;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PublishedMapSnapshot;
import com.uitopic.rumbo.planning.domain.services.IndoorMapCommandService;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates locked retrieval, domain validation, and atomic publication. */
@Service
@Profile("postgres")
public class IndoorMapCommandServiceImpl implements IndoorMapCommandService {
  private final IndoorMapRepository repository;
  private final PublishedMapRepository published;

  public IndoorMapCommandServiceImpl(IndoorMapRepository repository,
                                     PublishedMapRepository published) {
    this.repository = repository;
    this.published = published;
  }

  @Override
  @Transactional
  public IndoorMap handle(CreateDraftMapVersionCommand command) {
    if (!repository.lockMapForUpdate(command.siteId(), command.mapId())) {
      throw new MapVersionNotFoundException();
    }
    if (repository.hasDraft(command.mapId())) {
      throw new DraftMapVersionAlreadyExistsException();
    }
    PublishedMapSnapshot source;
    try {
      source = published.findBySite(command.siteId())
          .orElseThrow(MapVersionNotFoundException::new);
    } catch (InvalidPublishedMapDataException error) {
      throw new InvalidPublishedMapVersionException(error);
    }
    if (!source.mapId().equals(command.mapId())) {
      throw new MapVersionNotFoundException();
    }
    if (!source.modelVersionId().equals(command.sourceVersionId())) {
      throw new PublishedMapVersionChangedException();
    }
    IndoorMap draft;
    try {
      draft = IndoorMap.draftFromPublished(source, UUID.randomUUID());
    } catch (IllegalArgumentException | IllegalStateException error) {
      throw new InvalidPublishedMapVersionException(error);
    }
    repository.saveDraft(draft);
    return draft;
  }

  @Override
  @Transactional
  public IndoorMap handle(PublishMapVersionCommand command) {
    if (!repository.lockMapForUpdate(command.siteId(), command.mapId())) {
      throw new MapVersionNotFoundException();
    }
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
