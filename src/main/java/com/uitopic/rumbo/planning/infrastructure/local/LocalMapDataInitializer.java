package com.uitopic.rumbo.planning.infrastructure.local;

import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import com.uitopic.rumbo.planning.domain.model.commands.PublishMapVersionCommand;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionAccessibility;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.DirectoryEntryType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapScale;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PlanCoordinateSpace;
import com.uitopic.rumbo.planning.domain.repositories.IndoorMapRepository;
import com.uitopic.rumbo.planning.domain.repositories.PublishedMapRepository;
import com.uitopic.rumbo.planning.domain.services.IndoorMapCommandService;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Adds disposable example maps after migrations, only for the local PostgreSQL profile. */
@Component
@Profile("postgres & local")
public final class LocalMapDataInitializer implements ApplicationRunner {
  private static final UUID PUBLISHED_SITE = id("10000000-0000-4000-8000-000000000001");
  private static final UUID PUBLISHED_MAP = id("10000000-0000-4000-8000-000000000002");
  private static final UUID PUBLISHED_VERSION = id("10000000-0000-4000-8000-000000000003");
  private static final UUID DRAFT_SITE = id("20000000-0000-4000-8000-000000000001");
  private static final UUID DRAFT_MAP = id("20000000-0000-4000-8000-000000000002");
  private static final UUID DRAFT_VERSION = id("20000000-0000-4000-8000-000000000003");

  private final IndoorMapRepository drafts;
  private final PublishedMapRepository published;
  private final IndoorMapCommandService commands;

  public LocalMapDataInitializer(IndoorMapRepository drafts, PublishedMapRepository published,
      IndoorMapCommandService commands) {
    this.drafts = drafts;
    this.published = published;
    this.commands = commands;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (published.findBySite(PUBLISHED_SITE).isEmpty()) {
      if (drafts.findDraft(PUBLISHED_MAP, PUBLISHED_VERSION).isEmpty()) {
        drafts.saveDraft(example(PUBLISHED_MAP, PUBLISHED_SITE, PUBLISHED_VERSION,
            "10000000-0000-4000-8000-0000000000", "Cardiologia", "Cardiologia"));
      }
      commands.handle(new PublishMapVersionCommand(PUBLISHED_SITE, PUBLISHED_MAP,
          PUBLISHED_VERSION));
    }
    if (published.findBySite(DRAFT_SITE).isEmpty()
        && drafts.findDraft(DRAFT_MAP, DRAFT_VERSION).isEmpty()) {
      drafts.saveDraft(example(DRAFT_MAP, DRAFT_SITE, DRAFT_VERSION,
          "20000000-0000-4000-8000-0000000000", "Farmacia", null));
    }
  }

  private static IndoorMap example(UUID mapId, UUID siteId, UUID versionId,
      String idPrefix, String destinationName, String specialty) {
    UUID floorId = id(idPrefix + "04");
    UUID planVersionId = id(idPrefix + "05");
    UUID entranceId = id(idPrefix + "06");
    UUID destinationId = id(idPrefix + "07");
    IndoorMap map = new IndoorMap(mapId, siteId, versionId);
    map.addPlan(new PlanCoordinateSpace(floorId, planVersionId, 20, 20));
    map.setScale(new MapScale(floorId, planVersionId, 1));
    map.addNode(entranceId, floorId, planVersionId, 1, 1, NodeType.ACCESS);
    map.addNode(destinationId, floorId, planVersionId, 6, 1, NodeType.DESTINATION);
    map.addConnection(id(idPrefix + "08"), entranceId, destinationId,
        ConnectionType.CORRIDOR, 5, 5, 0, ConnectionAccessibility.ACCESSIBLE, true);
    map.addDestination(id(idPrefix + "09"), destinationId,
        specialty == null ? DirectoryEntryType.SERVICE : DirectoryEntryType.DOCTOR,
        destinationName, specialty);
    return map;
  }

  private static UUID id(String value) {
    return UUID.fromString(value);
  }
}
